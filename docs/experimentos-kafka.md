# Experimentos com o cluster Kafka local

Roteiro para observar na prática o comportamento do Kafka: chave e partição, consumer groups, falha de broker, garantias de escrita e replay de offsets.

**Como usar:** antes de cada passo, escreva na tabela de registro o que você **prevê** que vai acontecer. Depois execute e anote o **resultado**. Os resultados que surpreendem são os que mais ensinam.

## Pré-requisitos

- Cluster em execução (`docker compose -f docker/docker-compose.yml up -d`).
- Kafka UI (interface web) em http://localhost:8081.
- Atalho para os comandos, para não repetir o caminho (rode no terminal onde for executar os experimentos):

```bash
K=/opt/kafka/bin
```

Todos os comandos abaixo usam `docker exec ... kafka-1 $K/...`. Para os que precisam de mais de um terminal, abra uma aba de terminal por consumidor/produtor.

Glossário rápido de siglas usadas:
- **rf** (*replication factor*, fator de replicação): número de cópias de cada partição.
- **ISR** (*in-sync replicas*, réplicas em sincronia): réplicas que acompanham a líder.
- **acks** (*acknowledgements*, confirmações): quantas réplicas confirmam a escrita.
- **KRaft** (*Kafka Raft*): modo do Kafka sem ZooKeeper, em que os próprios brokers formam o quórum de controle.

## Experimento 1: chave e partição

**Objetivo:** ver que a mesma chave cai sempre na mesma partição, e que várias chaves podem dividir uma partição.

Crie o tópico (3 partições, rf=3):

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic lab.chaves --partitions 3 --replication-factor 3
```

Publique 6 mensagens com chave (formato `chave:valor`):

```bash
printf 'conta-A:dep-100\nconta-B:dep-50\nconta-A:saq-30\nconta-C:dep-70\nconta-A:dep-50\nconta-B:saq-20\n' | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab.chaves --property parse.key=true --property key.separator=:
```

Leia mostrando partição, offset e chave:

```bash
docker exec kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab.chaves --from-beginning --timeout-ms 8000 --property print.key=true --property print.partition=true --property print.offset=true
```

(O erro `TimeoutException` no final é esperado: é o `--timeout-ms` encerrando a leitura.)

**Prever antes:** em quantas partições cada conta vai aparecer? Duas contas podem cair na mesma partição?

**Observar:**
- Todas as mensagens de `conta-A` na mesma partição, com offsets crescentes (a ordem se mantém).
- Contas diferentes podem compartilhar partição (numa execução de referência, `conta-A` e `conta-C` caíram juntas).
- Repita publicando sem chave (remova `parse.key` e envie só o valor) e veja as mensagens se espalharem.

## Experimento 2: ordem só dentro da partição

**Objetivo:** confirmar que não há ordem global entre partições.

Use o tópico do experimento 1. Releia com o consumidor acima e compare a ordem de leitura **entre** partições (que pode variar) com a ordem **dentro** de cada partição (sempre crescente).

**Prever:** se eu ler duas vezes o tópico inteiro, a ordem global das mensagens será a mesma nas duas leituras?

**Observar:** a intercalação entre partições muda entre execuções; a sequência de offsets dentro de cada partição, não.

## Experimento 3: consumer groups e rebalance

**Objetivo:** ver as partições sendo divididas entre consumidores do mesmo grupo e redistribuídas quando um sai ou entra.

Abra **3 terminais** e, em cada um, inicie um consumidor com o mesmo grupo (um por terminal, um de cada vez, observando entre as etapas):

```bash
docker exec -it kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab.chaves --group g1 --property print.partition=true
```

Em um quarto terminal, acompanhe a distribuição:

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --describe --group g1
```

Sequência:
1. Suba **1** consumidor. Rode o `--describe`.
2. Suba o **2º**. Rode o `--describe`.
3. Suba o **3º**. Rode o `--describe`.
4. Suba um **4º** (o tópico tem 3 partições).
5. Encerre um dos consumidores (Ctrl+C) e rode o `--describe` de novo.
6. Em outro terminal, publique mensagens com chaves variadas (comando do experimento 1) e veja qual consumidor recebe o quê.

**Prever:** quantas partições cada consumidor recebe em cada etapa? O que o 4º consumidor faz?

**Observar:**
- 1 consumidor lê as 3 partições; com 3, uma cada.
- O 4º fica ocioso (sem partição): o paralelismo máximo é o número de partições.
- Ao sair um consumidor, as partições dele vão para os que restam (rebalance), e pode haver uma pausa curta.
- Mensagens da mesma chave sempre chegam ao mesmo consumidor (enquanto a atribuição não muda).

## Experimento 4: dois grupos, mesma mensagem

**Objetivo:** ver que grupos diferentes leem tudo de forma independente.

Inicie um consumidor com `--group g1` e outro com `--group g2`, ambos com `--from-beginning`, e publique mensagens.

**Prever:** cada mensagem chega a quantos consumidores?

**Observar:** as duas mensagens chegam a ambos os grupos. Dentro de um mesmo grupo, só a um consumidor.

## Experimento 5: falha de broker e ISR

**Objetivo:** ver a troca de líder e a redução do ISR quando um broker cai.

Veja o estado inicial:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic lab.chaves
```

Pare um broker (aqui, o `kafka-2`):

```bash
docker compose -f docker/docker-compose.yml stop kafka-2
```

Rode o `--describe` de novo (espere alguns segundos). Publique mensagens com `acks=all`:

```bash
printf 'conta-A:teste-falha\n' | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab.chaves --producer-property acks=all --property parse.key=true --property key.separator=:
```

Suba o broker de volta e acompanhe o ISR voltar:

```bash
docker compose -f docker/docker-compose.yml start kafka-2
```

**Prever:** quais partições mudam de líder? O ISR de cada partição perde qual broker? A escrita com `acks=all` funciona com 1 broker fora?

**Observar:**
- As partições que tinham o broker 2 como líder passam a ter outra líder.
- O ISR de todas as partições perde o broker 2 e volta a ter 3 membros depois que ele retorna.
- A escrita funciona (ISR de 2 satisfaz `min.insync.replicas=2`).
- O líder "preferido" pode demorar a voltar ao broker original.

## Experimento 6: `acks` e `min.insync.replicas`

**Objetivo:** ver a recusa de escrita por réplicas insuficientes e a diferença entre `acks=all` e `acks=1`.

Crie um tópico com `min.insync.replicas=3` (igual ao rf), de modo que **qualquer** broker fora bloqueie a escrita com `acks=all`. Isso mantém o quórum do KRaft intacto (2 de 3 nós), ao contrário de derrubar 2 brokers.

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --create --topic lab.durabilidade --partitions 1 --replication-factor 3 --config min.insync.replicas=3
```

Com os 3 brokers de pé, publique com `acks=all` (deve funcionar):

```bash
echo "k:ok" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab.durabilidade --producer-property acks=all --property parse.key=true --property key.separator=:
```

Pare um broker que não seja o `kafka-1` (que atende os comandos):

```bash
docker compose -f docker/docker-compose.yml stop kafka-3
```

Verifique o ISR e tente publicar com `acks=all`, depois com `acks=1`:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --describe --topic lab.durabilidade
```
```bash
echo "k:falha" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab.durabilidade --producer-property acks=all --producer-property delivery.timeout.ms=8000 --producer-property request.timeout.ms=4000 --property parse.key=true --property key.separator=:
```
```bash
echo "k:acks1" | docker exec -i kafka-1 $K/kafka-console-producer.sh --bootstrap-server kafka-1:9092 --topic lab.durabilidade --producer-property acks=1 --property parse.key=true --property key.separator=:
```

Suba o broker de volta:

```bash
docker compose -f docker/docker-compose.yml start kafka-3
```

**Prever:** o que acontece com `acks=all` com ISR de 2 e mínimo de 3? E com `acks=1`?

**Observar:**
- Com `acks=all`: avisos `NOT_ENOUGH_REPLICAS` e, ao esgotar as tentativas, a mensagem não é gravada.
- Com `acks=1`: a escrita é aceita, porque o `min.insync.replicas` só vale com `acks=all`.
- Depois de religar o broker, o ISR volta a 3 réplicas.

## Experimento 7: replay de offsets

**Objetivo:** reler o histórico de um grupo já consumido.

Consuma tudo com um grupo novo e deixe-o terminar:

```bash
docker exec kafka-1 $K/kafka-console-consumer.sh --bootstrap-server kafka-1:9092 --topic lab.chaves --group g-replay --from-beginning --timeout-ms 8000 --property print.partition=true --property print.offset=true
```

Veja o offset commitado e o lag (deve ser 0):

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --describe --group g-replay
```

Simule o reposicionamento (`--dry-run` só mostra o que faria), depois execute (o grupo precisa estar sem consumidores ativos):

```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --group g-replay --topic lab.chaves --reset-offsets --to-earliest --dry-run
```
```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --group g-replay --topic lab.chaves --reset-offsets --to-earliest --execute
```

Rode o consumidor do primeiro comando outra vez, sem `--from-beginning`.

**Prever:** o consumidor relê as mensagens? O `--from-beginning` faz diferença nessa segunda execução?

**Observar:**
- Depois do reset, o grupo relê tudo, mesmo sem `--from-beginning`, porque o offset commitado agora aponta para o início.
- Antes do reset, rodar com `--from-beginning` num grupo que já tem offset não faz nada: a opção só vale quando não há offset commitado.
- Outras formas de reposicionar: `--to-offset N`, `--to-datetime AAAA-MM-DDThh:mm:ss.sss`, `--shift-by -N`.

## Limpeza

Remova os tópicos e grupos criados nos experimentos:

```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --delete --topic lab.chaves
```
```bash
docker exec kafka-1 $K/kafka-topics.sh --bootstrap-server kafka-1:9092 --delete --topic lab.durabilidade
```
```bash
docker exec kafka-1 $K/kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 --delete --group g1
```

(Repita o último para `g2` e `g-replay`.) Se um grupo ainda tiver membros ativos, pare os consumidores antes.

## Tabela de registro

Copie e preencha a cada experimento.

| # | Prevejo que... | Aconteceu que... | O que me surpreendeu |
|---|---|---|---|
| 1 | | | |
| 2 | | | |
| 3 | | | |
| 4 | | | |
| 5 | | | |
| 6 | | | |
| 7 | | | |

## Observações

- Os tópicos de experimento usam o prefixo `lab.`, para não se misturar com os tópicos da aplicação criados por `docker/create-topics.sh`.
- Os comandos deste roteiro foram executados no cluster do projeto antes de serem documentados.
- O experimento 6 usa `min.insync.replicas=3` de propósito. Derrubar 2 de 3 brokers também faz o quórum do KRaft perder a maioria (os nós são brokers e controladores ao mesmo tempo), o que mistura dois efeitos.
