# Documentação

Convenção: pasta `docs/` na raiz, com decisões de arquitetura registradas como ADRs (Architecture Decision Records, formato Michael Nygard) em `docs/adr/`.

## Índice

- [Cluster Kafka local](kafka-cluster-local.md): como subir e usar o ambiente em `docker/`.
- [Experimentos com o cluster Kafka](experimentos-kafka.md): roteiro prático de chave, consumer groups, falhas, `acks` e replay.

## Decisões de arquitetura (ADRs)

- [0001. Cluster Kafka local com 3 brokers em KRaft](adr/0001-cluster-kafka-local-kraft-3-brokers.md)

## Regras

- ADRs são numerados em sequência (`NNNN-titulo-em-kebab-case.md`) e nunca são apagados. Uma decisão substituída recebe o status `Substituída por NNNN`.
- Cada ADR tem: Status, Contexto, Decisão e Consequências.
- Guias de uso (como rodar, operar) ficam na raiz de `docs/`, em arquivos nomeados pelo assunto.
