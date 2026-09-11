"""Execute the production Room cancellation SQL against SQLite, including race orderings."""
import pathlib, re, sqlite3, unittest
ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'android/app/src/main/kotlin/com/elysium369/meet/ride/data/local/RideCommandOutboxDao.kt').read_text()
def sql(method):
    blocks = re.findall(r'@Query\((.*?)\)\s*(?:suspend )?fun (\w+)', SOURCE, re.S)
    raw = next(raw for raw, name in blocks if name == method).strip().rstrip(',').strip()
    return raw[3:-3] if raw.startswith('"""') else raw[1:-1]
class CancellationSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.executescript('''
        CREATE TABLE ride_command_outbox(idempotencyKey TEXT PRIMARY KEY, rideId TEXT, actorSessionUserId TEXT, commandType TEXT, status TEXT, attemptCount INTEGER, updatedAt INTEGER, lastErrorCode TEXT, expectedVersion INTEGER, nextAttemptAt INTEGER, leaseStartedAt INTEGER);
        CREATE TABLE ride_requests(requestId TEXT PRIMARY KEY, passengerId TEXT, assignedDriverId TEXT, serverVersion INTEGER, status TEXT, syncState TEXT);
        CREATE TABLE active_ride_selections(rideRequestId TEXT, ownerPrincipalId TEXT);
        INSERT INTO ride_requests VALUES('r','a',NULL,0,'OPEN','PENDING');
        INSERT INTO active_ride_selections VALUES('r','a');
        INSERT INTO ride_command_outbox VALUES('publish','r','a','PUBLISH','PENDING',0,0,NULL,0,0,NULL);
        INSERT INTO ride_command_outbox VALUES('cancel','r','a','CANCEL','PENDING',0,0,NULL,0,0,NULL);
        ''')
    def run_sql(self, method, **extra):
        return self.db.execute(sql(method), dict(rideId='r', owner='a', now=1, key='cancel', **extra)).rowcount
    def test_definitely_unsent_is_superseded_atomically(self):
        with self.db:
            self.assertEqual(1, self.run_sql('supersedeUnsentPublication'))
            self.run_sql('finishLocalCancellation')
            self.run_sql('cancelUnpublishedRequest')
            self.run_sql('clearLocalCancelledSelection')
        self.assertEqual(('CANCELLED','LOCAL_CANCELLED'), self.db.execute('SELECT status,syncState FROM ride_requests').fetchone())
        self.assertEqual(0, self.db.execute('SELECT count(*) FROM active_ride_selections').fetchone()[0])
        self.assertEqual(0, self.run_sql('acquire', idempotencyKey='publish'))
    def test_inflight_or_lost_ack_is_not_local_success(self):
        for state in ('IN_FLIGHT','RETRYABLE','ACKNOWLEDGED'):
            self.db.execute("UPDATE ride_command_outbox SET status=?,attemptCount=1 WHERE idempotencyKey='publish'", (state,))
            self.assertEqual(0, self.run_sql('supersedeUnsentPublication'))
            self.assertEqual('OPEN', self.db.execute('SELECT status FROM ride_requests').fetchone()[0])
    def test_twenty_deliveries_cannot_supersede_twice(self):
        results = [self.run_sql('supersedeUnsentPublication') for _ in range(20)]
        self.assertEqual(1,sum(results))
    def test_other_owner_cannot_cancel(self):
        self.db.execute("UPDATE ride_requests SET passengerId='b'")
        self.assertEqual(0,self.run_sql('supersedeUnsentPublication'))
        self.assertEqual(0,self.run_sql('cancelUnpublishedRequest'))
    def test_resolved_version_is_immutable_once_sent(self):
        self.db.execute("UPDATE ride_command_outbox SET status='IN_FLIGHT' WHERE idempotencyKey='cancel'")
        self.assertEqual(1,self.run_sql('resolveCancellationVersion',version=7))
        self.assertEqual(0,self.run_sql('resolveCancellationVersion',version=8))
if __name__ == '__main__': unittest.main()
