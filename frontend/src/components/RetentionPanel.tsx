import { useState, useEffect } from 'react'
import { useRetentionConfig, useUpdateRetentionConfig } from '../hooks/useRetentionConfig'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from './ui/card'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Badge } from './ui/badge'
import { AlertCircle, CheckCircle, Database, Save, XCircle } from 'lucide-react'

const MAX_BYTES_LIMIT = 10737418240 // 10 GB in bytes
const MAX_AGE_DAYS_LIMIT = 3650 // 10 years

export default function RetentionPanel() {
  const { data: config, isLoading, error } = useRetentionConfig()
  const updateMutation = useUpdateRetentionConfig()

  // Form state
  const [maxBytes, setMaxBytes] = useState<string>('')
  const [maxAgeDays, setMaxAgeDays] = useState<string>('')

  // Feedback state
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [validationErrors, setValidationErrors] = useState<{maxBytes?: string; maxAgeDays?: string}>({})

  // Initialize form with current values
  useEffect(() => {
    if (config) {
      setMaxBytes(config.maxBytes != null ? String(config.maxBytes) : '')
      setMaxAgeDays(config.maxAgeDays != null ? String(config.maxAgeDays) : '')
    }
  }, [config])

  // Clear feedback after 5 seconds
  useEffect(() => {
    if (saveSuccess || saveError) {
      const timer = setTimeout(() => {
        setSaveSuccess(false)
        setSaveError(null)
      }, 5000)
      return () => clearTimeout(timer)
    }
  }, [saveSuccess, saveError])

  const formatBytes = (bytes: number | null | undefined): string => {
    if (bytes == null) return 'N/A'
    if (bytes === 0) return '0 Bytes'
    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
  }

  const calculatePercentage = (): number | null => {
    if (!config || config.maxBytes == null || config.maxBytes === 0) return null
    return Math.round((config.approxDbBytes / config.maxBytes) * 100)
  }

  const validateForm = (): boolean => {
    const errors: {maxBytes?: string; maxAgeDays?: string} = {}

    // Validate maxBytes
    if (maxBytes !== '') {
      const bytesValue = parseInt(maxBytes, 10)
      if (isNaN(bytesValue)) {
        errors.maxBytes = 'Must be a valid number'
      } else if (bytesValue < 0) {
        errors.maxBytes = 'Must be non-negative'
      } else if (bytesValue > MAX_BYTES_LIMIT) {
        errors.maxBytes = `Cannot exceed ${formatBytes(MAX_BYTES_LIMIT)}`
      }
    }

    // Validate maxAgeDays
    if (maxAgeDays !== '') {
      const daysValue = parseInt(maxAgeDays, 10)
      if (isNaN(daysValue)) {
        errors.maxAgeDays = 'Must be a valid number'
      } else if (daysValue < 0) {
        errors.maxAgeDays = 'Must be non-negative'
      } else if (daysValue > MAX_AGE_DAYS_LIMIT) {
        errors.maxAgeDays = `Cannot exceed ${MAX_AGE_DAYS_LIMIT} days`
      }
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleSave = async () => {
    if (!validateForm()) return

    setSaveSuccess(false)
    setSaveError(null)

    try {
      const updatePayload = {
        maxBytes: maxBytes !== '' ? parseInt(maxBytes, 10) : null,
        maxAgeDays: maxAgeDays !== '' ? parseInt(maxAgeDays, 10) : null,
        approxDbBytes: config?.approxDbBytes || 0,
        updatedAt: config?.updatedAt || new Date().toISOString(),
      }

      await updateMutation.mutateAsync(updatePayload)
      setSaveSuccess(true)
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to update retention config')
    }
  }

  const percentage = calculatePercentage()
  const isWarning = percentage !== null && percentage >= 80

  if (error) {
    return (
      <Card className="border-destructive">
        <CardHeader>
          <CardTitle className="text-destructive flex items-center gap-2">
            <AlertCircle className="h-5 w-5" />
            Error Loading Retention Configuration
          </CardTitle>
          <CardDescription>{error.message}</CardDescription>
        </CardHeader>
      </Card>
    )
  }

  if (isLoading || !config) {
    return (
      <Card>
        <CardContent className="p-6">
          <div className="flex items-center justify-center h-32">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Database className="h-5 w-5" />
              Retention Policy
            </CardTitle>
            <CardDescription>
              Manage database storage limits and data retention policies
            </CardDescription>
          </div>
          {isWarning && (
            <Badge variant="destructive" className="flex items-center gap-1">
              <AlertCircle className="h-3 w-3" />
              Storage Warning
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Current Status */}
        <div className="space-y-4">
          <div>
            <h4 className="text-sm font-semibold mb-2">Current Database Usage</h4>
            <div className="flex items-center justify-between mb-1">
              <span className="text-2xl font-bold">{formatBytes(config.approxDbBytes)}</span>
              {percentage !== null && (
                <span className={`text-lg font-semibold ${isWarning ? 'text-destructive' : 'text-muted-foreground'}`}>
                  {percentage}%
                </span>
              )}
            </div>
            {config.maxBytes != null && (
              <>
                <div className="w-full h-3 bg-muted rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all ${
                      isWarning ? 'bg-destructive' : 'bg-primary'
                    }`}
                    style={{ width: `${Math.min(percentage || 0, 100)}%` }}
                  />
                </div>
                <p className="text-xs text-muted-foreground mt-1">
                  Limit: {formatBytes(config.maxBytes)}
                </p>
              </>
            )}
            {config.maxBytes == null && (
              <p className="text-xs text-muted-foreground">No storage limit configured</p>
            )}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <h5 className="text-sm font-medium mb-1">Max Storage (Bytes)</h5>
              <p className="text-lg font-semibold">
                {config.maxBytes != null ? formatBytes(config.maxBytes) : 'No limit'}
              </p>
            </div>
            <div>
              <h5 className="text-sm font-medium mb-1">Max Age (Days)</h5>
              <p className="text-lg font-semibold">
                {config.maxAgeDays != null ? `${config.maxAgeDays} days` : 'No limit'}
              </p>
            </div>
          </div>

          <div>
            <h5 className="text-sm font-medium mb-1">Last Updated</h5>
            <p className="text-sm text-muted-foreground">
              {new Date(config.updatedAt).toLocaleString('en-US', {
                year: 'numeric',
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
              })}
            </p>
          </div>
        </div>

        {/* Configuration Form */}
        <div className="border-t pt-4">
          <h4 className="text-sm font-semibold mb-4">Update Retention Limits</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium mb-1 block">
                Max Storage (Bytes)
              </label>
              <Input
                type="number"
                placeholder="Leave empty for no limit"
                value={maxBytes}
                onChange={(e) => {
                  setMaxBytes(e.target.value)
                  setValidationErrors(prev => ({ ...prev, maxBytes: undefined }))
                }}
                onBlur={() => {
                  // Validate on blur
                  const errors: {maxBytes?: string; maxAgeDays?: string} = {...validationErrors}

                  if (maxBytes !== '') {
                    const bytesValue = parseInt(maxBytes, 10)
                    if (isNaN(bytesValue)) {
                      errors.maxBytes = 'Must be a valid number'
                    } else if (bytesValue < 0) {
                      errors.maxBytes = 'Must be non-negative'
                    } else if (bytesValue > MAX_BYTES_LIMIT) {
                      errors.maxBytes = `Cannot exceed ${formatBytes(MAX_BYTES_LIMIT)}`
                    } else {
                      delete errors.maxBytes
                    }
                  } else {
                    delete errors.maxBytes
                  }

                  setValidationErrors(errors)
                }}
                min="0"
                max={MAX_BYTES_LIMIT}
              />
              {validationErrors.maxBytes && (
                <p className="text-xs text-destructive mt-1">{validationErrors.maxBytes}</p>
              )}
              <p className="text-xs text-muted-foreground mt-1">
                Max: {formatBytes(MAX_BYTES_LIMIT)}
              </p>
            </div>

            <div>
              <label className="text-sm font-medium mb-1 block">
                Max Age (Days)
              </label>
              <Input
                type="number"
                placeholder="Leave empty for no limit"
                value={maxAgeDays}
                onChange={(e) => {
                  setMaxAgeDays(e.target.value)
                  setValidationErrors(prev => ({ ...prev, maxAgeDays: undefined }))
                }}
                onBlur={() => {
                  // Validate on blur
                  const errors: {maxBytes?: string; maxAgeDays?: string} = {...validationErrors}

                  if (maxAgeDays !== '') {
                    const daysValue = parseInt(maxAgeDays, 10)
                    if (isNaN(daysValue)) {
                      errors.maxAgeDays = 'Must be a valid number'
                    } else if (daysValue < 0) {
                      errors.maxAgeDays = 'Must be non-negative'
                    } else if (daysValue > MAX_AGE_DAYS_LIMIT) {
                      errors.maxAgeDays = `Cannot exceed ${MAX_AGE_DAYS_LIMIT} days`
                    } else {
                      delete errors.maxAgeDays
                    }
                  } else {
                    delete errors.maxAgeDays
                  }

                  setValidationErrors(errors)
                }}
                min="0"
                max={MAX_AGE_DAYS_LIMIT}
              />
              {validationErrors.maxAgeDays && (
                <p className="text-xs text-destructive mt-1">{validationErrors.maxAgeDays}</p>
              )}
              <p className="text-xs text-muted-foreground mt-1">
                Max: {MAX_AGE_DAYS_LIMIT} days (10 years)
              </p>
            </div>
          </div>

          <div className="mt-4 flex items-center gap-2">
            <Button
              onClick={handleSave}
              disabled={updateMutation.isPending || Object.keys(validationErrors).length > 0}
              className="flex items-center gap-2"
            >
              <Save className="h-4 w-4" />
              {updateMutation.isPending ? 'Saving...' : 'Save Configuration'}
            </Button>

            {saveSuccess && (
              <div className="flex items-center gap-1 text-sm text-green-600">
                <CheckCircle className="h-4 w-4" />
                Configuration updated successfully
              </div>
            )}

            {saveError && (
              <div className="flex items-center gap-1 text-sm text-destructive">
                <XCircle className="h-4 w-4" />
                {saveError}
              </div>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
