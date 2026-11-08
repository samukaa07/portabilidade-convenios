# Plano de testes críticos

1. Opt-in: registrar autorização e repetir sem duplicar beneficiário/convênio.
2. Agenda: negar consulta sem opt-in; listar parcelas ordenadas quando autorizado.
3. Elegibilidade: rejeitar parcela inexistente, de outro beneficiário ou não ABERTA.
4. Idempotência: repetir a mesma chave e payload e verificar mesmo ID/protocolo; concorrência com duas requisições
   simultâneas.
5. HMAC: payload adulterado ou assinatura incorreta deve ser rejeitado.
6. Assíncrono: registrar com HTTP 202 e protocolo; manter PENDENTE até prazo e depois confirmar/rejeitar.
7. Auditoria: garantir evento de solicitação e evento de transição, sem atualização destrutiva.
8. Falha: indisponibilidade, timeout, resposta inesperada e recuperação em retry.
9. Frontend: seleção, validação, exibição de estados e consulta da auditoria.
