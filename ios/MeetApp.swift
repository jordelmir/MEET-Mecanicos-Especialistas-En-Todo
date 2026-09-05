// SwiftUI iOS Foundation for MEET
// This is a starter stub — full implementation follows the Android architecture

import SwiftUI

@main
struct MeetApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        TabView {
            DashboardView()
                .tabItem { Label("Home", systemImage: "house") }
            VehiclesView()
                .tabItem { Label("Vehicles", systemImage: "car") }
            RidesView()
                .tabItem { Label("Rides", systemImage: "road.lanes") }
            MessagesView()
                .tabItem { Label("Messages", systemImage: "message") }
        }
    }
}

struct DashboardView: View {
    var body: some View {
        NavigationStack {
            VStack {
                Text("MEET")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                Text("Mecánicos Especialistas En Todo")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            .navigationTitle("Dashboard")
        }
    }
}

struct VehiclesView: View {
    var body: some View {
        NavigationStack {
            Text("Vehicles")
                .navigationTitle("Vehicles")
        }
    }
}

struct RidesView: View {
    var body: some View {
        NavigationStack {
            Text("Rides")
                .navigationTitle("Rides")
        }
    }
}

struct MessagesView: View {
    var body: some View {
        NavigationStack {
            Text("Messages")
                .navigationTitle("Messages")
        }
    }
}
