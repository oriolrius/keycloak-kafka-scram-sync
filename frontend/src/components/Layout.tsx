import { Link, useLocation } from 'react-router-dom'
import { Home, Clock } from 'lucide-react'

export default function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation()

  const isActive = (path: string) => {
    return location.pathname === path
  }

  return (
    <div className="min-h-screen bg-background">
      <nav className="border-b">
        <div className="container mx-auto px-4">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center gap-6">
              <Link to="/" className="font-bold text-xl">
                Keycloak Sync Agent
              </Link>
              <div className="flex gap-1">
                <Link
                  to="/"
                  className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                    isActive('/')
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:text-foreground hover:bg-muted'
                  }`}
                >
                  <Home className="h-4 w-4" />
                  Dashboard
                </Link>
                <Link
                  to="/operations"
                  className={`flex items-center gap-2 px-4 py-2 rounded-md transition-colors ${
                    isActive('/operations')
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:text-foreground hover:bg-muted'
                  }`}
                >
                  <Clock className="h-4 w-4" />
                  Operations
                </Link>
              </div>
            </div>
          </div>
        </div>
      </nav>
      <main>{children}</main>
    </div>
  )
}
