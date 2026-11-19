FROM traefik:v3.7.18@sha256:9c437ac4f09ce6f4980a8544902d3475fdf70a1a7c578729581d2535dfa7d635
COPY traefik.yaml /etc/traefik/traefik.yaml
COPY dynamic /etc/traefik/dynamic
USER 65532:65532
EXPOSE 8000 8443
CMD ["--configFile=/etc/traefik/traefik.yaml"]
