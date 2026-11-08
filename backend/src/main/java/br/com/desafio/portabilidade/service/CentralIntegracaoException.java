package br.com.desafio.portabilidade.service;

public class CentralIntegracaoException extends RuntimeException {

    public enum Tipo {INDISPONIVEL, TIMEOUT, RESPOSTA_INESPERADA, ASSINATURA_INVALIDA, REQUISICAO_RECUSADA}

    private final Tipo tipo;

    public CentralIntegracaoException(Tipo tipo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.tipo = tipo;
    }

    public CentralIntegracaoException(Tipo tipo, String mensagem) {
        this(tipo, mensagem, null);
    }

    public Tipo getTipo() {
        return tipo;
    }

    public boolean isRetentavel() {
        return tipo == Tipo.INDISPONIVEL || tipo == Tipo.TIMEOUT;
    }

    @Override
    public String getMessage() {
        return tipo + ": " + super.getMessage();
    }
}
