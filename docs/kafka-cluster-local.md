# Cluster Kafka local

Configuração em `docker/` para subir um cluster Kafka de desenvolvimento e criar os tópicos do laboratório.

## Arquivos

| Arquivo | Função |
|---|---|
| `docker/docker-compose.yml` | Define brokers, Schema Registry, Kafka UI e o job de criação de tópicos |
| `docker/create-topics.sh` | Script idempotente que cria os tópicos (`--if-not-exists`) |

## Serviços

| Serviço | Imagem | Porta no host | Observação |
|---|---|---|---|
| `kafka-1` | `apache/kafka:3.9.0` | 19092 | Broker + controller (KRaft), com healthcheck |
| `kafka-2` | `apache/kafka:3.9.0` | 29092 | Broker + controller (KRaft) |
| `kafka-3` | `apache/kafka:3.9.0` | 39092 | Broker + controller (KRaft) |
| `schema-registry` | `confluentinc/cp-schema-registry:7.7.1` | 8082 | Espera `kafka-1` ficar saudável |
| `kafka-ui` | `provectuslabs/kafka-ui:latest` | 8081 | Interface web |
| `kafka-init` | `apache/kafka:3.9.0` | n/a | Roda `create-topics.sh` e termina (exit 0) |

O projeto do Compose se chama `events-processor`.

## Como usar

A partir da raiz do projeto:

```bash
docker compose -f docker/docker-compose.yml up -d
```

Parar e remover os containers (os dados são perdidos, pois não há volumes):

```bash
docker compose -f docker/docker-compose.yml down
```

Ver o estado, incluindo o `kafka-init` que já terminou:

```bash
docker compose -f docker/docker-compose.yml ps -a
```

## Conexão

- Da aplicação rodando no host: `localhost:19092,localhost:29092,localhost:39092`.
- De outro container na mesma rede do Compose: `kafka-1:9092,kafka-2:9092,kafka-3:9092`.
- Schema Registry: http://localhost:8082.
- Kafka UI: http://localhost:8081.
- A porta 8080 fica livre para o serviço da aplicação.

Cada broker tem dois listeners: `INTERNAL` (porta 9092, entre containers) e `EXTERNAL` (porta 19092/29092/39092, para o host). O listener `CONTROLLER` (9093) é só do quórum KRaft.

## Configuração do cluster

| Parâmetro | Valor | Motivo |
|---|---|---|
| Modo | KRaft, 3 nós com papéis broker e controller | Sem ZooKeeper; quórum tolera 1 falha |
| `default.replication.factor` | 3 | Cada partição existe nos 3 brokers |
| `min.insync.replicas` | 2 | Com `acks=all`, escrita exige 2 réplicas; tolera 1 broker fora |
| `offsets.topic.replication.factor` | 3 | Offsets de consumer groups replicados |
| `transaction.state.log.replication.factor` / `min.isr` | 3 / 2 | Suporta producer transacional |
| `num.partitions` | 3 | Padrão, usado só se o tópico não definir |
| `auto.create.topics.enable` | false | Tópicos são criados de propósito pelo script |
| `group.initial.rebalance.delay.ms` | 0 | Consumer groups sobem sem atraso em dev |

## Tópicos

Definidos na variável `TOPICS` de `docker/create-topics.sh`, no formato `nome:partições:replicação`.

| Tópico | Partições | Replicação | Uso planejado |
|---|---|---|---|
| `payments.transfers` | 6 | 3 | Comandos/eventos de transferência |
| `payments.transfers.dlq` | 3 | 3 | Mensagens que falharam após retries |
| `ledger.events` | 6 | 3 | Eventos imutáveis do ledger |
| `ad.clicks` | 6 | 3 | Cliques brutos para agregação |
| `ad.clicks.aggregated` | 3 | 3 | Contagens por janela |

Para adicionar um tópico, inclua uma linha em `TOPICS` e rode novamente:

```bash
docker compose -f docker/docker-compose.yml up kafka-init
```

Alternativamente, criação manual:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic meu.topico --partitions 6 --replication-factor 3
```

## Experimentos de falha

Derrubar e restaurar um broker:

```bash
docker compose -f docker/docker-compose.yml stop kafka-2
```
```bash
docker compose -f docker/docker-compose.yml start kafka-2
```

Ver líderes e ISR de um tópico:

```bash
docker exec kafka-1 /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic payments.transfers
```

Com 1 broker parado, escritas com `acks=all` continuam funcionando (2 réplicas em sincronia). Com 2 parados, falham com `NotEnoughReplicas`, e o quórum KRaft também perde a maioria.

## Limitações conhecidas

- Sem volumes: `down` apaga todos os dados.
- Sem autenticação nem TLS (PLAINTEXT), apenas para uso local.
- A imagem `provectuslabs/kafka-ui:latest` não tem versão fixada.
- O nome com ponto e o underscore podem colidir em métricas, e o Kafka avisa disso na criação dos tópicos. Evite misturar os dois separadores nos nomes.
