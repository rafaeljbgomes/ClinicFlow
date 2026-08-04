{{- define "clinicflow.labels" -}}
app.kubernetes.io/part-of: clinicflow
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "clinicflow.selectorLabels" -}}
app.kubernetes.io/part-of: clinicflow
{{- end }}

{{- define "clinicflow.postgresSecretName" -}}
{{- default (printf "%s-postgres" .Release.Name) .Values.postgres.existingSecret -}}
{{- end }}

{{- define "clinicflow.rabbitmqSecretName" -}}
{{- default (printf "%s-rabbitmq" .Release.Name) .Values.rabbitmq.existingSecret -}}
{{- end }}

{{- define "clinicflow.configName" -}}
{{- printf "%s-config" .Release.Name -}}
{{- end }}
