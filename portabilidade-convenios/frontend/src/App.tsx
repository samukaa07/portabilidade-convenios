import {useEffect, useRef, useState} from 'react';

type Parcela = { id: string; convenioId: string; valor: number; dataDesconto: string; status: string };
type Operacao = {
    id: string;
    beneficiarioId: string;
    protocolo: string;
    status: string;
    motivoRejeicao?: string;
    criadaEm: string
};
type Evento = { id: number; evento: string; detalhe: string; ocorridoEm: string };
type Detalhe = { operacao: Operacao; parcelas: string[]; eventos: Evento[] };
type Conta = { conta: { banco: string; agencia: string; conta: string }; atualizadaEm: string; operacaoId: string };
const API = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

class ApiError extends Error {
    status: number;
    body: any;

    constructor(m: string, s: number, b: any) {
        super(m);
        this.status = s;
        this.body = b;
    }
}

async function request(path: string, init?: RequestInit) {
    const r = await fetch(API + path, {
        ...init,
        headers: {'Content-Type': 'application/json', ...(init?.headers || {})}
    });
    const body = await r.json().catch(() => ({}));
    if (!r.ok) throw new ApiError(body.erro || body.motivoRejeicao || `Erro HTTP ${r.status}`, r.status, body);
    return body;
}

export default function App() {
    const [beneficiario, setBeneficiario] = useState('12345678900'), [parcelas, setParcelas] = useState<Parcela[]>([]), [selecionadas, setSelecionadas] = useState<string[]>([]), [operacoes, setOperacoes] = useState<Operacao[]>([]), [mensagem, setMensagem] = useState(''), [loading, setLoading] = useState(false);
    const [detalheOp, setDetalheOp] = useState<Detalhe | null>(null), [contaAtual, setContaAtual] = useState<Conta | null>(null);
    const [banco, setBanco] = useState('001'), [agencia, setAgencia] = useState('1234'), [conta, setConta] = useState('98765-0'), [credor, setCredor] = useState('Minha Instituição Financeira');
    const envio = useRef(false), chave = useRef<{ key: string; fingerprint: string } | null>(null),
        detalheId = useRef<string | null>(null);

    async function carregar() {
        try {
            const [p, o] = await Promise.all([request(`/beneficiarios/${beneficiario}/agenda`), request('/operacoes')]);
            setParcelas(p);
            setOperacoes(o);
        } catch (e) {
            setMensagem((e as Error).message);
        }
        request(`/beneficiarios/${beneficiario}/conta-recebimento`).then(setContaAtual).catch(() => setContaAtual(null));
        if (detalheId.current) request(`/operacoes/${detalheId.current}`).then(setDetalheOp).catch(() => {
        });
    }

    useEffect(() => {
        carregar();
        const t = setInterval(() => {
            request('/operacoes').then(setOperacoes).catch(() => {
            });
            request(`/beneficiarios/${beneficiario}/agenda`).then(setParcelas).catch(() => {
            });
            request(`/beneficiarios/${beneficiario}/conta-recebimento`).then(setContaAtual).catch(() => {
            });
            if (detalheId.current) request(`/operacoes/${detalheId.current}`).then(setDetalheOp).catch(() => {
            });
        }, 4000);
        return () => clearInterval(t);
    }, [beneficiario]);

    async function autorizar() {
        setLoading(true);
        try {
            await request('/autorizacoes', {
                method: 'POST',
                body: JSON.stringify({beneficiarioId: beneficiario, convenioId: 'CONV-001'})
            });
            setMensagem('Autorização registrada.');
            await carregar();
        } catch (e) {
            setMensagem((e as Error).message)
        } finally {
            setLoading(false)
        }
    }

    async function criar() {
        if (envio.current) return;
        envio.current = true;
        setLoading(true);
        const body = JSON.stringify({
            beneficiarioId: beneficiario,
            parcelas: [...selecionadas].sort(),
            credorCessionario: credor,
            contaRecebimento: {banco, agencia, conta}
        });
        // A mesma Idempotency-Key é reutilizada em reenvios do mesmo pedido (duplo clique, falha de rede) e só renovada após resposta definitiva.
        if (!chave.current || chave.current.fingerprint !== body) chave.current = {
            key: crypto.randomUUID(),
            fingerprint: body
        };
        try {
            const op = await request('/operacoes-portabilidade', {
                method: 'POST',
                headers: {'Idempotency-Key': chave.current.key},
                body
            });
            chave.current = null;
            setMensagem(`Operação enviada. ID ${op.id} — protocolo ${op.protocolo || 'indisponível'}`);
            setSelecionadas([]);
            await carregar();
        } catch (e) {
            if (e instanceof ApiError) {
                chave.current = null;
                setMensagem(e.status === 502 ? `Falha de integração com a Central: ${e.message}. Nenhuma parcela foi bloqueada; você pode tentar novamente.` : e.message);
                await carregar();
            } else setMensagem('Falha de rede. Ao reenviar, a mesma solicitação não será duplicada.');
        } finally {
            envio.current = false;
            setLoading(false)
        }
    }

    async function detalhe(id: string) {
        try {
            detalheId.current = id;
            setDetalheOp(await request(`/operacoes/${id}`));
        } catch (e) {
            setMensagem((e as Error).message)
        }
    }

    const money = (v: number) => v.toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
    return <main>
        <header>
            <div><span className="eyebrow">PLATAFORMA FINANCEIRA</span><h1>Portabilidade de convênios</h1><p>Gerencie
                autorizações, parcelas e operações de portabilidade.</p></div>
            <span className="pill">● Ambiente de demonstração</span></header>
        <section className="stats">
            <article><small>Parcelas
                disponíveis</small><strong>{parcelas.filter(p => p.status === 'ABERTA').length}</strong></article>
            <article><small>Operações registradas</small><strong>{operacoes.length}</strong></article>
            <article><small>Em
                processamento</small><strong>{operacoes.filter(o => o.status === 'PENDENTE').length}</strong></article>
        </section>
        <section className="panel">
            <div className="section-title">
                <div><span className="eyebrow">PASSO 01</span><h2>Beneficiário e autorização</h2></div>
            </div>
            <div className="form-row"><label>Identificador do beneficiário<input value={beneficiario}
                                                                                 onChange={e => setBeneficiario(e.target.value)}/></label>
                <button onClick={autorizar} disabled={loading}>Registrar opt-in</button>
                <button className="secondary" onClick={carregar}>Consultar agenda</button>
            </div>
        </section>
        <section className="panel">
            <div className="section-title">
                <div><span className="eyebrow">PASSO 02</span><h2>Agenda de descontos</h2></div>
                <span className="muted">{parcelas.length} parcelas</span></div>
            <div className="table-wrap">
                <table>
                    <thead>
                    <tr>
                        <th></th>
                        <th>Parcela</th>
                        <th>Convênio</th>
                        <th>Valor</th>
                        <th>Data</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>{parcelas.map(p => <tr key={p.id}>
                        <td><input type="checkbox" disabled={p.status !== 'ABERTA'}
                                   checked={selecionadas.includes(p.id)}
                                   onChange={e => setSelecionadas(s => e.target.checked ? [...s, p.id] : s.filter(x => x !== p.id))}/>
                        </td>
                        <td>{p.id}</td>
                        <td>{p.convenioId}</td>
                        <td>{money(p.valor)}</td>
                        <td>{new Date(p.dataDesconto + 'T12:00:00').toLocaleDateString('pt-BR')}</td>
                        <td><span className={'status ' + p.status.toLowerCase()}>{p.status}</span></td>
                    </tr>)}</tbody>
                </table>
                {!parcelas.length &&
                    <p className="empty">Nenhuma parcela encontrada. Registre o opt-in e consulte novamente.</p>}</div>
        </section>
        <section className="panel">
            <div className="section-title">
                <div><span className="eyebrow">PASSO 03</span><h2>Nova portabilidade</h2></div>
                <span className="muted">{selecionadas.length} selecionada(s)</span></div>
            {contaAtual && <p className="muted">Conta de recebimento vigente: banco {contaAtual.conta.banco} /
                ag. {contaAtual.conta.agencia} / conta {contaAtual.conta.conta} (atualizada
                em {new Date(contaAtual.atualizadaEm).toLocaleString('pt-BR')})</p>}
            <div className="form-grid"><label>Credor cessionário<input value={credor}
                                                                       onChange={e => setCredor(e.target.value)}/></label><label>Banco<input
                value={banco} onChange={e => setBanco(e.target.value)}/></label><label>Agência<input value={agencia}
                                                                                                     onChange={e => setAgencia(e.target.value)}/></label><label>Conta<input
                value={conta} onChange={e => setConta(e.target.value)}/></label></div>
            <button disabled={loading || !selecionadas.length} onClick={criar}>Solicitar portabilidade</button>
        </section>
        <section className="panel">
            <div className="section-title">
                <div><span className="eyebrow">ACOMPANHAMENTO</span><h2>Histórico de operações</h2></div>
            </div>
            <div className="table-wrap">
                <table>
                    <thead>
                    <tr>
                        <th>Operação</th>
                        <th>Protocolo Central</th>
                        <th>Beneficiário</th>
                        <th>Status</th>
                        <th>Data</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>{operacoes.map(o => <tr key={o.id}>
                        <td>{o.id.slice(0, 8)}...</td>
                        <td>{o.protocolo || '—'}</td>
                        <td>{o.beneficiarioId}</td>
                        <td><span className={'status ' + o.status.toLowerCase()}>{o.status}</span>{o.motivoRejeicao &&
                            <div className="muted">{o.motivoRejeicao}</div>}</td>
                        <td>{new Date(o.criadaEm).toLocaleString('pt-BR')}</td>
                        <td>
                            <button className="link" onClick={() => detalhe(o.id)}>Auditoria</button>
                        </td>
                    </tr>)}</tbody>
                </table>
                {!operacoes.length && <p className="empty">Ainda não existem operações.</p>}</div>
        </section>
        {detalheOp && <section className="panel">
            <div className="section-title">
                <div><span className="eyebrow">TRILHA DE AUDITORIA</span>
                    <h2>Operação {detalheOp.operacao.id.slice(0, 8)} — <span
                        className={'status ' + detalheOp.operacao.status.toLowerCase()}>{detalheOp.operacao.status}</span>
                    </h2><span
                        className="muted">Protocolo: {detalheOp.operacao.protocolo || '—'} • Parcelas: {detalheOp.parcelas.join(', ')}</span>
                </div>
                <button className="secondary" onClick={() => {
                    detalheId.current = null;
                    setDetalheOp(null)
                }}>Fechar
                </button>
            </div>
            <ol className="timeline">{detalheOp.eventos.map(ev => <li key={ev.id}><strong>{ev.evento}</strong><span
                className="muted"> • {new Date(ev.ocorridoEm).toLocaleString('pt-BR')}</span>
                <div>{ev.detalhe}</div>
            </li>)}</ol>
        </section>}
        {mensagem && <aside>
            <div><strong>Retorno do sistema</strong>
                <button className="close" onClick={() => setMensagem('')}>×</button>
            </div>
            <pre>{mensagem}</pre>
        </aside>}
        <footer>Desafio técnico • Java + Spring Boot • React + TypeScript</footer>
    </main>
}