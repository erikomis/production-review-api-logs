<div align="center">

<img src="docs/logo.svg" alt="" width="72" height="72" />

# Production Review API · Logs

**Serviço de auditoria do ecossistema ReviewStore: consome os eventos da API pelo Kafka, grava no MongoDB e expõe uma API interna de consulta.**
O [painel administrativo](https://github.com/erikomis/dashboard-production-review-react) lê esses dados pela [API principal](https://github.com/erikomis/production-review-api), que funciona como proxy.

<p>
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-3C50E0?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img alt="Spring Boot 3.3" src="https://img.shields.io/badge/Spring_Boot-3.3-3C50E0?style=for-the-badge&logo=springboot&logoColor=white" />
  <img alt="Apache Kafka" src="https://img.shields.io/badge/Kafka-KRaft-3C50E0?style=for-the-badge&logo=apachekafka&logoColor=white" />
  <img alt="MongoDB" src="https://img.shields.io/badge/MongoDB-7-3C50E0?style=for-the-badge&logo=mongodb&logoColor=white" />
</p>
<p>
  <img alt="Gradle" src="https://img.shields.io/badge/build-Gradle-1C2434?style=flat-square" />
  <img alt="Dead letter topic" src="https://img.shields.io/badge/erros-DLT-1C2434?style=flat-square" />
  <img alt="Token interno" src="https://img.shields.io/badge/API-X--Internal--Token-1C2434?style=flat-square" />
  <img alt="Testcontainers" src="https://img.shields.io/badge/testes-Testcontainers_+_EmbeddedKafka-067647?style=flat-square" />
</p>

[Visão geral](#-visão-geral) ·
[Como rodar](#-como-rodar) ·
[Endpoints](#-endpoints) ·
[Evento](#-formato-do-evento) ·
[Testes](#-testes) ·
[Ecossistema](#-ecossistema-production-review)

</div>

<br />

---

## 🔭 Visão geral

```mermaid
flowchart LR
    Painel["📊 Painel admin<br/>React 18"] -->|"GET /admin/activity"| API

    subgraph API["Production Review API · :8084"]
      direction TB
      Svc["Services"] --> Prod["Produtor Kafka"]
      Proxy["Proxy /admin/activity"]
    end

    Prod -->|"evento JSON"| Topic{{"Kafka<br/>production-review-api"}}
    Topic --> Consumer

    subgraph Logs["Serviço de logs · :8089"]
      direction TB
      Consumer["Consumidor<br/>idempotente por eventId"] --> Mongo[("MongoDB<br/>log_notification")]
      Rest["API /api/v1/logs"] --> Mongo
    end

    Consumer -.->|"mensagem inválida"| DLT{{"production-review-api.DLT"}}
    Proxy -->|"X-Internal-Token"| Rest
```

| Recurso | Destaques |
|---|---|
| 📥 **Consumo** | Aceita o evento novo (com `eventId`, `type`, `occurredAt`...) e o formato legado (`action/message/nameUser`), que vira `type="LEGACY"` |
| ♻️ **Idempotência** | Índice único esparso em `eventId`: reentregas do Kafka são ignoradas sem erro |
| 🪦 **Dead letter** | JSON inválido ou data ilegível vai direto para `production-review-api.DLT`; falhas transitórias têm 2 novas tentativas antes. A partição nunca trava |
| 🔎 **Consulta** | Página filtrável por tipo, entidade, usuário, texto e período, sempre em `occurredAt` DESC |
| 📊 **Resumo** | Totais por tipo e por dia no fuso `America/Sao_Paulo`, sem buracos no intervalo |
| 🔐 **Acesso** | Header `X-Internal-Token` obrigatório; sem token configurado a API recusa tudo |
| 📈 **Saúde** | Actuator em `/actuator/health` (público) |

## 🚀 Como rodar

### Pré-requisitos

- **Java 17** (o projeto inclui o Gradle Wrapper: `./gradlew`)
- **Docker** para MongoDB e Kafka (e para os testes de integração)

### 1. Suba as dependências

Se a [API principal](https://github.com/erikomis/production-review-api) já está rodando com Kafka e MongoDB, pule este passo. Senão:

```bash
docker compose -f docker-compose.dev.yml up -d   # MongoDB :27017, Kafka KRaft :9092, Kafdrop :19000
```

### 2. Rode o serviço

```bash
export MONGODB_URI='mongodb://root:senha123@localhost:27017/logs-production-review?authSource=admin'
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export LOGS_API_TOKEN=local-logs-token

./gradlew bootRun
```

O serviço sobe em **http://localhost:8089**. Para conferir:

```bash
curl http://localhost:8089/actuator/health                                          # {"status":"UP"}
curl -H 'X-Internal-Token: local-logs-token' http://localhost:8089/api/v1/logs       # página de logs
```

Para gerar eventos sem a API, publique direto no tópico:

```bash
docker exec -i prv-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic production-review-api <<'EOF'
{"eventId":"7d5c2a9e-0000-4000-8000-000000000001","type":"REVIEW_CREATED","action":"Avaliação criada","message":"Usuário Teste avaliou Smartphone X com 5 estrelas","nameUser":"Usuário Teste","userId":2,"entityType":"REVIEW","entityId":"12","occurredAt":"2026-10-02T03:10:00Z"}
{"action":"Avaliação criada","message":"Formato antigo","nameUser":"Fulano"}
EOF
```

> [!NOTE]
> Na API principal, configure `LOGS_SERVICE_URL=http://localhost:8089` e o **mesmo** `LOGS_API_TOKEN` para o proxy `/admin/activity` funcionar.

### Com Docker

```bash
cp .env.example .env    # preencha MONGO_ROOT_PASSWORD e LOGS_API_TOKEN
docker compose up -d --build
```

A imagem é multi-stage, com cache das dependências do Gradle numa camada própria, e o runtime usa JRE 17 com usuário sem privilégios.

### Variáveis de ambiente

<details open>
<summary><b>Obrigatórias</b></summary>

| Variável | Descrição |
|---|---|
| `MONGODB_URI` | Conexão completa com o MongoDB, credenciais incluídas. Sem ela, o padrão é `mongodb://localhost:27017/logs-production-review` **sem autenticação** |
| `LOGS_API_TOKEN` | Token esperado no header `X-Internal-Token`. **Sem valor padrão** de propósito: vazio, a API responde 401 a tudo e registra um aviso no startup |

</details>

<details>
<summary><b>Opcionais</b></summary>

| Variável | Padrão | Descrição |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers do Kafka |
| `KAFKA_TOPIC` | `production-review-api` | Tópico consumido (a DLT é `<tópico>.DLT`) |
| `KAFKA_CONSUMER_GROUP_ID` | `logs-production-review` | Grupo de consumo |
| `PORT` | `8089` | Porta HTTP |

</details>

## 🐳 Docker e deploy

As imagens são publicadas **privadas** no GitHub Container Registry: `ghcr.io/erikomis/production-review-api-logs`.

```bash
docker compose up -d --build
```

```mermaid
flowchart LR
    T["tests<br/>gradle test<br/>push na main"] -->|sucesso| P["publish<br/>build do commit testado"]
    P --> GHCR[("ghcr.io (privado)<br/>latest · sha-commit")]
    GHCR --> D["deploy<br/>login temporário + pull + up"]
    D --> VPS["VPS<br/>docker compose"]
```

- **publish**: só roda depois que os testes do push na `main` passam; builda exatamente o commit testado e publica as tags `latest` e `sha-<commit>`, autenticando com o `GITHUB_TOKEN` do próprio workflow.
- **deploy**: entra na VPS por SSH, faz login no GHCR com o token temporário do job, sobe a imagem daquele commit e faz logout. **Nenhuma credencial fica salva na VPS.** O deploy fica desligado até você criar a variável `DEPLOY_ENABLED=true`.

| Tipo | Nome | Para quê |
|---|---|---|
| Secret | `HOST`, `USERNAME`, `SSH_KEY` | Acesso SSH à VPS |
| Variável (opcional) | `DEPLOY_DIR` | Pasta do `docker-compose.yml` na VPS (padrão: `logs`) |
| Variável | `DEPLOY_ENABLED` | Crie com o valor `true` para liberar o deploy. Sem ela, o workflow só publica a imagem |

> [!IMPORTANT]
> Antes do primeiro deploy, copie o `docker-compose.yml` deste repositório para a pasta da VPS. Depois do primeiro publish, confira em **Perfil → Packages → production-review-api-logs → Package settings** que a visibilidade está **Private**.

## 📡 Endpoints

Todas as rotas em `/api/v1` exigem o header `X-Internal-Token`. Sem ele, ou com valor errado, a resposta é **401**.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/v1/logs` | Página de logs, `occurredAt` DESC |
| `GET` | `/api/v1/logs/summary` | Totais por tipo e por dia |
| `GET` | `/actuator/health` | Saúde do serviço (**público**) |

### `GET /api/v1/logs`

| Parâmetro | Descrição |
|---|---|
| `page` · `size` | Página (a partir de 0) e tamanho (padrão 10, máximo 100). Valores fora da faixa caem no padrão, como na API principal |
| `type` | Tipo exato, ex.: `REVIEW_CREATED`, `LEGACY` |
| `entityType` | `USER`, `CATEGORY`, `SUBCATEGORY`, `PRODUCT`, `PRODUCT_IMAGE`, `REVIEW`, `IMPORT` |
| `userId` | Id do usuário que gerou o evento (numérico; senão, 400) |
| `search` | Texto em `action`, `message` ou `nameUser`, sem diferenciar maiúsculas (tratado como literal, não como regex) |
| `from` · `to` | ISO-8601: data (`2026-10-01`, vale o dia inteiro no fuso de São Paulo), data-hora com offset (`2026-10-01T10:00:00Z`) ou local (`2026-10-01T10:00:00`, em São Paulo). Ambos inclusivos |

```json
{
  "content": [
    {
      "id": "66fc6a1e9b1f2c3d4e5f6a7b",
      "eventId": "7d5c2a9e-0000-4000-8000-000000000001",
      "type": "REVIEW_CREATED",
      "action": "Avaliação criada",
      "message": "Usuário Teste avaliou Smartphone X com 5 estrelas",
      "nameUser": "Usuário Teste",
      "userId": 2,
      "entityType": "REVIEW",
      "entityId": "12",
      "occurredAt": "2026-10-02T03:10:00Z",
      "receivedAt": "2026-10-02T03:10:00.412Z"
    }
  ],
  "page": { "size": 10, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

### `GET /api/v1/logs/summary?from&to`

Os mesmos formatos de data. Sem `from`/`to`, resume os **últimos 30 dias** (incluindo hoje); com só um deles, o outro é calculado para fechar 30 dias. O intervalo máximo é de 366 dias. `byDay` tem **um item por dia** entre `from` e `to`, inclusive os dias sem eventos, agrupados no fuso `America/Sao_Paulo`.

```json
{
  "total": 3,
  "byType": [ { "type": "REVIEW_CREATED", "count": 2 }, { "type": "LEGACY", "count": 1 } ],
  "byDay":  [ { "date": "2026-09-30", "count": 0 }, { "date": "2026-10-01", "count": 3 } ]
}
```

### Erros

Mesmo formato da API principal:

```json
{ "message": "from: data inválida 'ontem'; use ISO-8601 (ex.: 2026-10-01 ou 2026-10-01T10:00:00Z)", "httpStatus": "BAD_REQUEST", "statusCode": 400 }
```

| Status | Quando |
|---|---|
| `400` | Data inválida, `from` depois de `to`, intervalo do resumo acima de 366 dias, `userId` não numérico |
| `401` | `X-Internal-Token` ausente ou errado, ou serviço sem `LOGS_API_TOKEN` configurado |

## 📨 Formato do evento

A API publica no tópico `production-review-api` um JSON (valor `String`):

```json
{
  "eventId": "uuid", "type": "REVIEW_CREATED", "action": "Avaliação criada",
  "message": "Usuário Teste avaliou Smartphone X com 5 estrelas", "nameUser": "Usuário Teste", "userId": 2,
  "entityType": "REVIEW", "entityId": "12", "occurredAt": "2026-10-02T03:10:00Z"
}
```

| Situação | Como é gravado |
|---|---|
| Evento com `type` | Todos os campos + `receivedAt`. Sem `occurredAt`, usa `receivedAt` |
| Evento sem `type` (legado: só `action/message/nameUser`) | `type="LEGACY"`, `occurredAt = receivedAt` |
| `eventId` já gravado | Ignorado (reentrega do Kafka) |
| JSON inválido, objeto vazio, `occurredAt` ilegível ou campo com tipo errado | Vai para `production-review-api.DLT`, com a exceção nos headers |

Documento na coleção `log_notification`:

| Campo | Índice |
|---|---|
| `eventId` | único e esparso (eventos legados não têm) |
| `occurredAt` | descendente |
| `type` · `userId` | simples |
| `action` · `message` · `nameUser` · `entityType` · `entityId` · `receivedAt` | — |

Tipos publicados hoje: `USER_SIGNED_UP`, `USER_ACTIVATED`, `USER_LOGGED_IN`, `USER_ROLE_CHANGED`, `USER_STATUS_CHANGED`, `CATEGORY_*`, `SUBCATEGORY_*`, `PRODUCT_*`, `PRODUCT_IMAGE_ADDED|REMOVED`, `REVIEW_CREATED|UPDATED|DELETED|HIDDEN|RESTORED` e `CATALOG_IMPORT_STARTED|COMPLETED|FAILED`. Tipos novos são aceitos sem mudança no serviço.

## 🧪 Testes

```bash
./gradlew test   # precisa do Docker rodando para os testes de integração
```

| Camada | O que cobre |
|---|---|
| **Mapeamento** (unitário) | Evento novo × legado, `occurredAt` com offset, coerção de ids, rejeição de JSON e datas inválidas |
| **Parâmetros de data** (unitário) | Data × data-hora, fuso de São Paulo, limites inclusivos |
| **API** (`@WebMvcTest`) | Formato de página, filtros repassados, paginação, 401 sem token ou com token errado, 401 com token não configurado, 400 de data e de `userId` |
| **Consultas** (`@DataMongoTest` + Testcontainers) | Filtros, busca literal sem diferenciar maiúsculas, ordenação, resumo por dia no fuso de São Paulo sem buracos, índices e idempotência |
| **Consumidor** (`@SpringBootTest` + `@EmbeddedKafka` + Testcontainers) | Fluxo Kafka → MongoDB, duplicata ignorada, formato legado, mensagem inválida na DLT sem travar as seguintes |

> [!NOTE]
> O MongoDB dos testes roda via **Testcontainers** (`mongo:7`, a mesma imagem do ambiente local) em vez do flapdoodle embedded: testa o servidor real, sem baixar binários por sistema operacional, inclusive o índice único esparso, o `$dateToString` com fuso e a regex. Sem Docker, essas classes são puladas.

O workflow **`tests.yml`** roda `./gradlew test` com JDK 17 em todo pull request e no push da `main`, e publica o relatório dos testes.

<details>
<summary><b>📁 Estrutura do projeto</b></summary>

```text
src/main/java/br/com/logsproductionreview/
├── config/          # relógio e fuso horário
├── dto/             # evento recebido, filtros e respostas da API
├── entity/          # documento MongoDB com índices
├── mapper/          # JSON do Kafka → documento (regra legado × novo)
├── message/         # consumidor Kafka e tratamento de erro com DLT
├── repositories/    # Spring Data MongoDB
├── security/        # filtro do X-Internal-Token
├── service/         # gravação idempotente, consultas e agregações
└── web/             # controller, parâmetros de data e erros
```

</details>

## 🧩 Ecossistema Production Review

| Projeto | Descrição |
|---|---|
| [**production-review-api**](https://github.com/erikomis/production-review-api) | API REST: autenticação, catálogo, avaliações; publica os eventos e faz o proxy da auditoria |
| [**production-review-api-logs**](https://github.com/erikomis/production-review-api-logs) | Este repositório: serviço de auditoria |
| [**dashboard-production-review-site**](https://github.com/erikomis/dashboard-production-review-site) | Site público onde as pessoas avaliam produtos |
| [**dashboard-production-review-react**](https://github.com/erikomis/dashboard-production-review-react) | Painel administrativo, com a tela de atividade alimentada por este serviço |

<div align="center">
<br />
<sub>Feito com ☕ e Spring Boot.</sub>
</div>
