# 0001. Cluster Kafka local com 3 brokers em KRaft

- Status: Aceita
- Data: 2026-11-05

## Contexto

O projeto exercita cenários de sistemas distribuídos (idempotência, at-least-once, particionamento, failover, degradação) e precisa de um Kafka local que se comporte como um cluster real.

## Decisão

Usar 3 brokers Kafka em modo KRaft (sem ZooKeeper), com replicação 3 e `min.insync.replicas=2`, mais Schema Registry e Kafka UI, definidos em `docker/docker-compose.yml`. Os tópicos são criados por um job `kafka-init` e a criação automática fica desligada.

## Consequências

- É possível derrubar 1 broker sem perda de disponibilidade nem de escrita com `acks=all`, o que permite testar failover.
- Consome mais memória que um único broker.
- Sem volumes, o `down` apaga os dados. Isso é aceitável em desenvolvimento.
- Sem autenticação nem TLS, então não serve para outro ambiente além do local.
