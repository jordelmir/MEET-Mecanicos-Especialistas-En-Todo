# RAG Retrieval Policy

Before modifying code, retrieve context in this order:

1. Files directly mentioned by the user.
2. Related tests.
3. Interfaces and contracts.
4. Migrations and DB schema.
5. CI workflows.
6. ADRs.
7. Project memory.
8. Previous loop reports.
9. External docs only when required.

If RAG contradicts code, code wins.