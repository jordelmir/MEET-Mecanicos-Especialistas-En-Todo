import { Routes, Route } from 'react-router-dom'
import { Layout } from './components/Layout'
import { DashboardPage } from './pages/Dashboard'
import { VehiclesPage } from './pages/Vehicles'
import { RidesPage } from './pages/Rides'
import { PropertiesPage } from './pages/Properties'
import { MessagesPage } from './pages/Messages'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="vehicles" element={<VehiclesPage />} />
        <Route path="rides" element={<RidesPage />} />
        <Route path="properties" element={<PropertiesPage />} />
        <Route path="messages" element={<MessagesPage />} />
      </Route>
    </Routes>
  )
}
