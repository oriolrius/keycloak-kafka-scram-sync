import { useSummary } from '../hooks/useSummary'
import { useHealth } from '../hooks/useHealth'
import { useReconcileStatus, useReconcileTrigger } from '../hooks/useReconcile'
import { useOperationsHistory } from '../hooks/useOperationsHistory'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import RetentionPanel from '../components/RetentionPanel'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { Activity, AlertCircle, Clock, Database, Server, RefreshCw } from 'lucide-react'

export default function Dashboard() {
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useSummary()
  const { data: health, isLoading: healthLoading } = useHealth()
  const { data: reconcileStatus } = useReconcileStatus()
  const { data: history24h, isLoading: history24Loading } = useOperationsHistory({ hours: 24 })
  const { data: history72h, isLoading: history72Loading } = useOperationsHistory({ hours: 72 })
  const reconcileTrigger = useReconcileTrigger()

  const handleForceReconcile = () => {
    reconcileTrigger.mutate()
  }

  // Extract connection status from health checks
  const kafkaHealth = health?.checks?.find((check) => check.name === 'kafka')
  const keycloakHealth = health?.checks?.find((check) => check.name === 'keycloak-admin-client')

  // Format bytes to MB
  const formatBytes = (bytes: number | undefined) => {
    if (!bytes) return '0 MB'
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
  }

  // Format timestamp for display
  const formatTime = (isoString: string | undefined) => {
    if (!isoString) return 'N/A'
    return new Date(isoString).toLocaleTimeString()
  }

  // Format chart data for Recharts
  const formatChartData = (data: any[] | undefined) => {
    if (!data) return []
    return data.map((point) => ({
      time: new Date(point.timestamp).toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
      }),
      operations: point.count,
      errors: point.errorCount,
    }))
  }

  if (summaryLoading) {
    return (
      <div className="p-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-muted rounded w-1/4"></div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-32 bg-muted rounded"></div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  if (summaryError) {
    return (
      <div className="p-8">
        <Card className="border-destructive">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2">
              <AlertCircle className="h-5 w-5" />
              Error Loading Dashboard
            </CardTitle>
            <CardDescription>{summaryError.message}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Dashboard</h1>
          <p className="text-muted-foreground">
            Keycloak Kafka Sync Agent operations monitoring
          </p>
        </div>
        <Button
          onClick={handleForceReconcile}
          disabled={reconcileTrigger.isPending || reconcileStatus?.running}
          className="flex items-center gap-2"
        >
          <RefreshCw className={`h-4 w-4 ${reconcileTrigger.isPending || reconcileStatus?.running ? 'animate-spin' : ''}`} />
          {reconcileStatus?.running ? 'Reconciliation Running...' : 'Force Reconcile'}
        </Button>
      </div>

      {reconcileTrigger.isSuccess && (
        <Card className="border-green-500 bg-green-50 dark:bg-green-950">
          <CardHeader>
            <CardTitle className="text-green-700 dark:text-green-300">
              Reconciliation Completed
            </CardTitle>
            <CardDescription className="text-green-600 dark:text-green-400">
              Successful: {reconcileTrigger.data.successfulOperations} | Failed: {reconcileTrigger.data.failedOperations} | Duration: {reconcileTrigger.data.durationMs}ms
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      {reconcileTrigger.isError && (
        <Card className="border-destructive bg-red-50 dark:bg-red-950">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2">
              <AlertCircle className="h-5 w-5" />
              Reconciliation Failed
            </CardTitle>
            <CardDescription className="text-red-600 dark:text-red-400">
              {reconcileTrigger.error.message}
            </CardDescription>
          </CardHeader>
        </Card>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Operations / Hour</CardTitle>
            <Activity className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{summary?.opsPerHour || 0}</div>
            <p className="text-xs text-muted-foreground">
              Last updated: {formatTime(summary?.timestamp)}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Error Rate</CardTitle>
            <AlertCircle className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {summary?.errorRate ? `${summary.errorRate.toFixed(2)}%` : '0%'}
            </div>
            <p className="text-xs text-muted-foreground">
              Past hour
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Latency (P95/P99)</CardTitle>
            <Clock className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {summary?.latencyP95 ?? 'N/A'} / {summary?.latencyP99 ?? 'N/A'}ms
            </div>
            <p className="text-xs text-muted-foreground">
              95th / 99th percentile
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Connection Status & Database Info */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Kafka Connection</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <div
                className={`h-3 w-3 rounded-full ${
                  kafkaHealth?.status === 'UP' ? 'bg-green-500' : 'bg-red-500'
                }`}
              ></div>
              <span className="text-lg font-semibold">
                {healthLoading ? 'Checking...' : kafkaHealth?.status || 'Unknown'}
              </span>
            </div>
            {kafkaHealth?.data && (
              <p className="text-xs text-muted-foreground mt-2">
                {kafkaHealth.data['bootstrap.servers']}
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Keycloak Connection</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <div
                className={`h-3 w-3 rounded-full ${
                  keycloakHealth?.status === 'UP' ? 'bg-green-500' : 'bg-red-500'
                }`}
              ></div>
              <span className="text-lg font-semibold">
                {healthLoading ? 'Checking...' : keycloakHealth?.status || 'Unknown'}
              </span>
            </div>
            {keycloakHealth?.data && (
              <p className="text-xs text-muted-foreground mt-2">
                Realm: {keycloakHealth.data.realm}
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Database Usage</CardTitle>
            <Database className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{formatBytes(summary?.dbUsageBytes)}</div>
            <p className="text-xs text-muted-foreground">SQLite database size</p>
          </CardContent>
        </Card>
      </div>

      {/* 24h Operations Volume Chart */}
      <Card>
        <CardHeader>
          <CardTitle>Operations Volume (24h)</CardTitle>
          <CardDescription>Hourly operations count over the last 24 hours</CardDescription>
        </CardHeader>
        <CardContent>
          {history24Loading ? (
            <div className="h-64 flex items-center justify-center">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={formatChartData(history24h)}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="operations"
                  stroke="hsl(var(--primary))"
                  strokeWidth={2}
                  name="Operations"
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>

      {/* 72h Operations & Error Rate Chart */}
      <Card>
        <CardHeader>
          <CardTitle>Operations & Errors (72h)</CardTitle>
          <CardDescription>Operations volume and error trends over 72 hours</CardDescription>
        </CardHeader>
        <CardContent>
          {history72Loading ? (
            <div className="h-64 flex items-center justify-center">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={formatChartData(history72h)}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <Tooltip />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="operations"
                  stroke="hsl(var(--primary))"
                  strokeWidth={2}
                  name="Operations"
                />
                <Line
                  type="monotone"
                  dataKey="errors"
                  stroke="hsl(var(--destructive))"
                  strokeWidth={2}
                  name="Errors"
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </CardContent>
      </Card>

      {/* Retention Policy Panel */}
      <RetentionPanel />
    </div>
  )
}
