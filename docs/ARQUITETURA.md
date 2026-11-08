# Arquitetura e fluxo

```mermaid
flowchart LR
    U[Usuário] --> R[React + TypeScript]
    R --> A[REST API Spring Boot]
    A --> S[PortabilidadeService]
    S --> DB[(H2 / PostgreSQL)]
    S --> C[CentralGateway / CentralHttpClient]
    C -->|HTTP + HMAC| M[Central simulada /central]
    C --> P[Protocolo assíncrono]
    P --> J[PollingScheduler]
    J --> C
    J --> DB
    DB --> AU[Eventos de auditoria]
```

## Estados

- PENDENTE: protocolo recebido, aguardando processamento.
- CONFIRMADA: Central confirmou; parcelas ficam PORTABILIZADA.
- REJEITADA: Central rejeitou; parcelas retornam para ABERTA.
- FALHA_INTEGRACAO: falha ao registrar na Central ou confirmação não obtida após esgotar as consultas; parcelas voltam
  para ABERTA. Estados finais são imutáveis.

## Integridade

A assinatura HMAC-SHA256 cobre uma representação canônica do pedido (parcelas ordenadas) e as respostas da Central
(`X-Assinatura`).

## Idempotência

`Idempotency-Key` única no banco + hash do payload + bloqueio pessimista das parcelas + repasse da chave à Central.
Reenvios devolvem a mesma operação.
