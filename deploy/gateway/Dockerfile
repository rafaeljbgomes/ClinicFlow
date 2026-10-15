FROM traefik:v3.7.13@sha256:24841fe2de7304c149343d877d2923b4c8800a38ba015dea9174c23b20e344a0
COPY traefik.yaml /etc/traefik/traefik.yaml
COPY dynamic /etc/traefik/dynamic
USER 65532:65532
EXPOSE 8000 8443
CMD ["--configFile=/etc/traefik/traefik.yaml"]
