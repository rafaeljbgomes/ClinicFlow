FROM traefik:v3.7.16@sha256:22a759fb803abb5c3a7ba5d5a55a201c5fb0fc8993d5c72c47a89075e64f1953
COPY traefik.yaml /etc/traefik/traefik.yaml
COPY dynamic /etc/traefik/dynamic
USER 65532:65532
EXPOSE 8000 8443
CMD ["--configFile=/etc/traefik/traefik.yaml"]
