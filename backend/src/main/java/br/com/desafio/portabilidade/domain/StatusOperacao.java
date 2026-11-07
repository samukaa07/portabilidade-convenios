package br.com.desafio.portabilidade.domain;

public enum StatusOperacao {
    PENDENTE, CONFIRMADA, REJEITADA, FALHA_INTEGRACAO;

    /**
     * Apenas PENDENTE pode evoluir; os demais estados são terminais.
     */
    public boolean podeTransitarPara(StatusOperacao destino) {
        return this == PENDENTE && destino != PENDENTE;
    }
}
