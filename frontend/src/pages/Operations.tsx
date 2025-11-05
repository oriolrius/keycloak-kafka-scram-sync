import { useState } from 'react'
import { useOperations } from '../hooks/useOperations'
import { OperationType, OperationResult } from '../types/api'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card'
import { Button } from '../components/ui/button'
import { Input } from '../components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '../components/ui/select'
import { Badge } from '../components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '../components/ui/collapsible'
import { Download, ChevronDown, ChevronUp, AlertCircle, CheckCircle, XCircle, MinusCircle } from 'lucide-react'

type SortField = 'occurredAt' | 'durationMs' | 'principal'
type SortDirection = 'asc' | 'desc'

export default function Operations() {
  // Pagination state
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(25)

  // Filter state
  const [startTime, setStartTime] = useState<string>('')
  const [endTime, setEndTime] = useState<string>('')
  const [principal, setPrincipal] = useState<string>('')
  const [opType, setOpType] = useState<OperationType | undefined>(undefined)
  const [result, setResult] = useState<OperationResult | undefined>(undefined)

  // Sort state
  const [sortField, setSortField] = useState<SortField>('occurredAt')
  const [sortDirection, setSortDirection] = useState<SortDirection>('desc')

  // Expanded rows state
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set())

  // Build query params
  const queryParams = {
    page,
    pageSize,
    ...(startTime && { startTime: new Date(startTime).toISOString() }),
    ...(endTime && { endTime: new Date(endTime).toISOString() }),
    ...(principal && { principal }),
    ...(opType && { opType }),
    ...(result && { result }),
  }

  // Fetch operations with polling
  const { data, isLoading, error } = useOperations(queryParams)

  // Sort operations client-side
  const sortedOperations = data?.operations
    ? [...data.operations].sort((a, b) => {
        let aVal, bVal
        switch (sortField) {
          case 'occurredAt':
            aVal = new Date(a.occurredAt).getTime()
            bVal = new Date(b.occurredAt).getTime()
            break
          case 'durationMs':
            aVal = a.durationMs
            bVal = b.durationMs
            break
          case 'principal':
            aVal = a.principal.toLowerCase()
            bVal = b.principal.toLowerCase()
            break
          default:
            return 0
        }
        if (sortDirection === 'asc') {
          return aVal > bVal ? 1 : -1
        }
        return aVal < bVal ? 1 : -1
      })
    : []

  // Toggle row expansion
  const toggleRow = (id: number) => {
    const newExpanded = new Set(expandedRows)
    if (newExpanded.has(id)) {
      newExpanded.delete(id)
    } else {
      newExpanded.add(id)
    }
    setExpandedRows(newExpanded)
  }

  // Toggle sort
  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDirection('desc')
    }
  }

  // CSV Export
  const handleExport = () => {
    if (!sortedOperations.length) return

    const headers = [
      'Timestamp',
      'Realm',
      'Principal',
      'Operation Type',
      'Entity ID',
      'Entity Type',
      'Result',
      'Duration (ms)',
      'Error Message',
    ]

    const rows = sortedOperations.map((op) => [
      new Date(op.occurredAt).toISOString(),
      op.entityId.split(':')[0] || '',
      op.principal,
      op.opType,
      op.entityId,
      op.entityType,
      op.result,
      op.durationMs.toString(),
      op.errorMessage || '',
    ])

    const csv = [
      headers.join(','),
      ...rows.map((row) =>
        row.map((cell) => `"${cell.replace(/"/g, '""')}"`).join(',')
      ),
    ].join('\n')

    const blob = new Blob([csv], { type: 'text/csv' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `operations-${new Date().toISOString()}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  // Reset filters
  const handleResetFilters = () => {
    setStartTime('')
    setEndTime('')
    setPrincipal('')
    setOpType(undefined)
    setResult(undefined)
    setPage(0)
  }

  // Format timestamp
  const formatTimestamp = (isoString: string) => {
    const date = new Date(isoString)
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  }

  // Extract realm from entityId (format: realm:entityType:id)
  const extractRealm = (entityId: string | undefined) => {
    if (!entityId) return 'N/A'
    return entityId.split(':')[0] || 'N/A'
  }

  // Result badge
  const ResultBadge = ({ result }: { result: OperationResult }) => {
    const variants = {
      SUCCESS: { variant: 'default' as const, icon: CheckCircle, color: 'text-green-600' },
      ERROR: { variant: 'destructive' as const, icon: XCircle, color: 'text-red-600' },
      SKIPPED: { variant: 'secondary' as const, icon: MinusCircle, color: 'text-gray-600' },
    }
    const config = variants[result]
    const Icon = config.icon

    return (
      <Badge variant={config.variant} className="flex items-center gap-1 w-fit">
        <Icon className={`h-3 w-3 ${config.color}`} />
        {result}
      </Badge>
    )
  }

  // Sort indicator
  const SortIndicator = ({ field }: { field: SortField }) => {
    if (sortField !== field) return null
    return sortDirection === 'asc' ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />
  }

  if (error) {
    return (
      <div className="p-8">
        <Card className="border-destructive">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2">
              <AlertCircle className="h-5 w-5" />
              Error Loading Operations
            </CardTitle>
            <CardDescription>{error.message}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="p-8 space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">Operation Timeline</h1>
          <p className="text-muted-foreground">
            Detailed history of all sync operations with filtering and export
          </p>
        </div>
        <Button
          onClick={handleExport}
          disabled={!sortedOperations.length}
          className="flex items-center gap-2"
        >
          <Download className="h-4 w-4" />
          Export CSV
        </Button>
      </div>

      {/* Filters */}
      <Card>
        <CardHeader>
          <CardTitle>Filters</CardTitle>
          <CardDescription>Filter operations by time range, principal, type, and result</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 lg:grid-cols-6 gap-4">
            <div>
              <label className="text-sm font-medium mb-1 block">Start Time</label>
              <Input
                type="datetime-local"
                value={startTime}
                onChange={(e) => {
                  setStartTime(e.target.value)
                  setPage(0)
                }}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">End Time</label>
              <Input
                type="datetime-local"
                value={endTime}
                onChange={(e) => {
                  setEndTime(e.target.value)
                  setPage(0)
                }}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">Principal</label>
              <Input
                placeholder="Filter by principal..."
                value={principal}
                onChange={(e) => {
                  setPrincipal(e.target.value)
                  setPage(0)
                }}
              />
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">Operation Type</label>
              <Select
                value={opType || 'all'}
                onValueChange={(value) => {
                  setOpType(value === 'all' ? undefined : (value as OperationType))
                  setPage(0)
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All types" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All types</SelectItem>
                  {Object.values(OperationType).map((type) => (
                    <SelectItem key={type} value={type}>
                      {type}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-sm font-medium mb-1 block">Result</label>
              <Select
                value={result || 'all'}
                onValueChange={(value) => {
                  setResult(value === 'all' ? undefined : (value as OperationResult))
                  setPage(0)
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="All results" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All results</SelectItem>
                  {Object.values(OperationResult).map((res) => (
                    <SelectItem key={res} value={res}>
                      {res}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-end">
              <Button variant="outline" onClick={handleResetFilters} className="w-full">
                Reset
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Operations Table */}
      <Card>
        <CardHeader>
          <div className="flex justify-between items-center">
            <div>
              <CardTitle>Operations</CardTitle>
              <CardDescription>
                Showing {sortedOperations.length} of {data?.totalElements || 0} operations
              </CardDescription>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Page size:</span>
              <Select
                value={pageSize.toString()}
                onValueChange={(value) => {
                  setPageSize(parseInt(value))
                  setPage(0)
                }}
              >
                <SelectTrigger className="w-20">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="25">25</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                  <SelectItem value="100">100</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex items-center justify-center h-64">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : sortedOperations.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-64 text-muted-foreground">
              <AlertCircle className="h-12 w-12 mb-4" />
              <p>No operations found</p>
            </div>
          ) : (
            <div className="border rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead></TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('occurredAt')}
                    >
                      <div className="flex items-center gap-1">
                        Timestamp
                        <SortIndicator field="occurredAt" />
                      </div>
                    </TableHead>
                    <TableHead>Realm</TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('principal')}
                    >
                      <div className="flex items-center gap-1">
                        Principal
                        <SortIndicator field="principal" />
                      </div>
                    </TableHead>
                    <TableHead>Operation Type</TableHead>
                    <TableHead>Result</TableHead>
                    <TableHead
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => handleSort('durationMs')}
                    >
                      <div className="flex items-center gap-1">
                        Duration
                        <SortIndicator field="durationMs" />
                      </div>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {sortedOperations.map((op) => (
                    <Collapsible
                      key={op.id}
                      open={expandedRows.has(op.id)}
                      onOpenChange={() => toggleRow(op.id)}
                      asChild
                    >
                      <>
                        <TableRow className="cursor-pointer hover:bg-muted/30">
                          <TableCell className="w-8">
                            <CollapsibleTrigger asChild>
                              <Button variant="ghost" size="sm" className="p-0 h-6 w-6">
                                {expandedRows.has(op.id) ? (
                                  <ChevronDown className="h-4 w-4" />
                                ) : (
                                  <ChevronUp className="h-4 w-4 rotate-180" />
                                )}
                              </Button>
                            </CollapsibleTrigger>
                          </TableCell>
                          <TableCell className="font-mono text-sm">
                            {formatTimestamp(op.occurredAt)}
                          </TableCell>
                          <TableCell>{extractRealm(op.entityId)}</TableCell>
                          <TableCell className="font-mono text-sm">{op.principal}</TableCell>
                          <TableCell>
                            <Badge variant="outline">{op.opType}</Badge>
                          </TableCell>
                          <TableCell>
                            <ResultBadge result={op.result} />
                          </TableCell>
                          <TableCell>{op.durationMs}ms</TableCell>
                        </TableRow>
                        {expandedRows.has(op.id) && (
                          <TableRow>
                            <TableCell colSpan={7}>
                              <CollapsibleContent className="p-4 bg-muted/30 rounded-lg space-y-2">
                                <div className="grid grid-cols-2 gap-4 text-sm">
                                  <div>
                                    <span className="font-semibold">Entity ID:</span>
                                    <p className="font-mono text-xs mt-1">{op.entityId}</p>
                                  </div>
                                  <div>
                                    <span className="font-semibold">Entity Type:</span>
                                    <p className="mt-1">{op.entityType}</p>
                                  </div>
                                  {op.errorMessage && (
                                    <div className="col-span-2">
                                      <span className="font-semibold text-destructive flex items-center gap-1">
                                        <AlertCircle className="h-4 w-4" />
                                        Error Details:
                                      </span>
                                      <p className="mt-1 text-destructive bg-destructive/10 p-2 rounded border border-destructive/20">
                                        {op.errorMessage}
                                      </p>
                                    </div>
                                  )}
                                </div>
                              </CollapsibleContent>
                            </TableCell>
                          </TableRow>
                        )}
                      </>
                    </Collapsible>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Pagination Controls */}
          {data && data.totalPages > 1 && (
            <div className="flex justify-between items-center mt-4">
              <div className="text-sm text-muted-foreground">
                Page {page + 1} of {data.totalPages}
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPage(Math.min(data.totalPages - 1, page + 1))}
                  disabled={page >= data.totalPages - 1}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
