# Plataforma de Portabilidade de Convênios

Implementação demonstrativa Fullstack React + Java. O fluxo cobre opt-in, consulta de agenda, seleção de parcelas,
solicitação idempotente, protocolo assíncrono da Central simulada, polling, atualização de status e auditoria.

## Stack

- Java 21, Spring Boot 3.3.4, Spring Data JPA, Maven
- React + TypeScript + Vite
- H2 para execução local sem infraestrutura; PostgreSQL 16 via Docker Compose
- HMAC-SHA256, Swagger/OpenAPI, JUnit 5

## Executar localmente pelo IntelliJ IDEA (sem Docker)

1. Instale JDK 21 e Maven (ou use Maven Wrapper se adicionar um).
2. Abra a pasta `backend` no IntelliJ como projeto Maven.
3. Aguarde a resolução das dependências.
4. Execute `PortabilidadeApplication`.
5. A API ficará em `http://localhost:8080`; Swagger em `http://localhost:8080/swagger-ui.html`.
6. Em outro terminal, entre em `frontend`, rode `npm install` e `npm run dev`.
7. Acesse `http://localhost:5173`.

O H2 é persistido em `backend/data` (se vier de versão anterior, apague essa pasta). Para console:
`http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:file:./data/portabilidade`, usuário `sa`, senha vazia.

A Central simulada roda no mesmo processo (`/central`), não exigindo passo adicional.

## Executar com Docker

Na raiz do repositório: `docker compose up --build`. Frontend: `http://localhost:5173`; API: `http://localhost:8080`;
Swagger: `http://localhost:8080/swagger-ui.html`.

## Dados de demonstração

- Beneficiário: `12345678900`
- Convênio: `CONV-001`
- Parcelas: `PAR-001`, `PAR-002`, `PAR-003`

O seed cria autorização e três parcelas na primeira inicialização do banco.

## Endpoints principais

| Método | Endpoint                                    | Finalidade                                                                            |
|--------|---------------------------------------------|---------------------------------------------------------------------------------------|
| POST   | `/api/autorizacoes`                         | Registrar opt-in                                                                      |
| GET    | `/api/beneficiarios/{id}/agenda`            | Consultar agenda autorizada                                                           |
| GET    | `/api/beneficiarios/{id}/conta-recebimento` | Conta de recebimento vigente (após confirmação)                                       |
| POST   | `/api/operacoes-portabilidade`              | Solicitar portabilidade (exige `Idempotency-Key`; 202, ou 502 em falha de integração) |
| GET    | `/api/operacoes`                            | Listar operações                                                                      |
| GET    | `/api/operacoes/{id}`                       | Detalhe, parcelas e auditoria                                                         |
| GET    | `/api/operacoes/{id}/auditoria`             | Trilha de auditoria                                                                   |
| GET    | `/api/health`                               | Health check                                                                          |

### Exemplo de solicitação

`POST /api/operacoes-portabilidade`
Header: `Idempotency-Key: chave-unica-123`

```json
{
  "beneficiarioId": "12345678900",
  "parcelas": ["PAR-001"],
  "credorCessionario": "Minha Instituicao",
  "contaRecebimento": {"banco":"001","agencia":"1234","conta":"98765-0"}
}
```

A API responde HTTP 202 e retorna protocolo. O agendador consulta a Central periodicamente; a confirmação ocorre após o
atraso configurado.

## Decisões arquiteturais

- Organização por domínio, repositório, serviço e camada web para separar responsabilidades; a Central é acessada por
  uma porta (`CentralGateway`) com implementação HTTP (`CentralHttpClient`), o que permite testar o fluxo com mock e
  trocar a Central real sem tocar na regra de negócio.
- HMAC-SHA256 assina o payload canônico enviado à Central (campo `assinatura`) e as respostas da Central (header
  `X-Assinatura`); respostas sem assinatura válida são tratadas como erro de integração.
- Idempotência em três camadas: `Idempotency-Key` com restrição única no banco, hash SHA-256 do payload (mesma chave com
  corpo diferente → 409), bloqueio pessimista das parcelas na reserva (concorrência) e repasse da mesma chave à Central,
  que também deduplica e devolve o mesmo protocolo.
- O registro é feito em 3 passos: reserva local em transação curta (operação PENDENTE + parcelas EM_PORTABILIDADE +
  evento) → chamada HTTP fora de transação → gravação do protocolo ou da falha (parcelas liberadas).
- Estados: `PENDENTE → CONFIRMADA | REJEITADA | FALHA_INTEGRACAO`; estados finais são imutáveis
  (`StatusOperacao.podeTransitarPara`). Toda mudança gera linha nova em `eventos_auditoria` (sem update/delete).
- Confirmação assíncrona: o agendador consulta somente operações com `proximaConsultaEm` vencido, usando backoff
  exponencial com jitter (1s, 2s, 4s… teto 30s), respeitando `Retry-After`. Após `POLLING_MAX_ATTEMPTS` (12) consultas
  sem resposta definitiva a operação vira `FALHA_INTEGRACAO` e as parcelas são liberadas.
- Ao confirmar, a conta de recebimento do beneficiário (`contas_beneficiario`) é atualizada e o evento
  `CONTA_RECEBIMENTO_ATUALIZADA` é registrado.

## Bibliotecas externas

- Spring Boot (Web, Data JPA, Validation): padrão de mercado, produtividade. `RestClient` (Spring) como cliente HTTP com
  timeouts configuráveis.
- H2 (execução local sem infraestrutura) e PostgreSQL (Docker).
- springdoc-openapi: Swagger UI para explorar a API.
- JUnit 5, Mockito e AssertJ (via spring-boot-starter-test).
- React 18 + Vite + TypeScript no frontend, sem bibliotecas de UI adicionais.

## Simulação e integração com a Central

A Central é simulada por endpoints HTTP no próprio backend sob `/central` (`CentralMockController` +
`CentralMockService`), seguindo o contrato do desafio e acessados via HTTP real pelo `CentralHttpClient`
(`CENTRAL_BASE_URL`):

- `POST /central/autorizacoes` → 201 com `autorizacaoId`; `GET /central/beneficiarios/{id}/agenda`.
- `POST /central/operacoes-portabilidade` → 202 `{protocolo}` (valida a assinatura HMAC; idempotente pela
  `Idempotency-Key`).
- `GET /central/operacoes-portabilidade/{protocolo}` → 204 + `Retry-After` enquanto pendente; 200 com `status`/
  `motivoRejeicao` após o atraso (`CENTRAL_DELAY_MS`, padrão 8s).
- Falhas simuladas: rejeição (`CENTRAL_FAILURE_RATE`, 0.15), HTTP 503 (`CENTRAL_UNAVAILABLE_RATE`, 0.10) e timeout
  (`CENTRAL_TIMEOUT_RATE`, 0.05; a Central demora mais que o read-timeout de 2s, mas registra a operação — a retentativa
  com a mesma chave evita duplicidade).
- O cliente traduz erros em `CentralIntegracaoException` (INDISPONIVEL, TIMEOUT, RESPOSTA_INESPERADA,
  ASSINATURA_INVALIDA, REQUISICAO_RECUSADA); no registro faz até 3 tentativas com backoff para erros retentáveis. A UI
  exibe a falha e permite nova tentativa.
- O frontend reaproveita a mesma `Idempotency-Key` enquanto o pedido é o mesmo e não houve resposta definitiva (duplo
  clique/falha de rede).
- O segredo HMAC é configurável por `CENTRAL_SECRET`. Para testes manuais determinísticos:
  `CENTRAL_FAILURE_RATE=0 CENTRAL_UNAVAILABLE_RATE=0 CENTRAL_TIMEOUT_RATE=0`.

## Testes

Execute `mvn test` dentro de `backend`. Cobertura: assinatura HMAC, backoff, idempotência (reenvio, concorrência com 6
threads, payload divergente, parcela já reservada), falhas de integração (registro e consulta, esgotamento de
tentativas, Retry-After) e transições de estado (confirmação com atualização de conta, rejeição, estados finais
imutáveis, trilha de eventos).

## Trade-offs e evoluções

- A Central mock guarda protocolos em memória; reiniciar a aplicação perde os protocolos simulados (as operações
  pendentes irão para FALHA_INTEGRACAO após esgotar as consultas). Em produção: circuit breaker, outbox/fila e
  reprocessamento de operações PENDENTE sem protocolo (a chave idempotente já permite reenvio seguro).
- Agendador simples em processo; em escala, usar fila ou scheduler distribuído com lock (ShedLock).
- Dados bancários sem mascaramento e sem autenticação/autorização; adicionar observabilidade, Testcontainers/PostgreSQL,
  testes de contrato e E2E.
- Esquema gerado por `ddl-auto: update`; usar Flyway em produção. Ao atualizar de uma versão anterior, apague
  `backend/data` (H2) para recriar o esquema.

## Uso de IA e harness

A IA foi utilizada como apoio à decomposição dos requisitos do desafio, desenho da arquitetura, geração assistida de
código e testes, revisão de lacunas contra o enunciado e documentação. Prompts representativos:

1. "Mapeie os requisitos funcionais e não funcionais do desafio e proponha uma arquitetura Java Spring Boot + React."
2. "Implemente fluxo de portabilidade com idempotência, HMAC, protocolo assíncrono e auditoria."
3. "Revise o código contra o enunciado e liste o que falta (Central via HTTP, backoff, conta de recebimento,
   idempotência concorrente, testes)."

Restrições aplicadas: código gerado tratado como rascunho sujeito a revisão; segredos externalizáveis por variáveis de
ambiente; dados de demonstração fictícios. Veja `docs/USO_IA.md`, `docs/ARQUITETURA.md` e `docs/PLANO_TESTES.md`.

