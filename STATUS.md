# Estado da entrega

Este projeto é uma primeira implementação funcional, **não a conclusão integral dos dois briefings**.

## Verificado no ambiente local

- Compilação do APK debug e do APK de testes de instrumentação.
- Quarenta e dois testes automatizados: cálculos, SemVer, sincronização, Room/SQLite, configuração do backend, seleção segura de releases, cache de atualizações, lista simplificada, compras pagas, migração, backup de histórico, cadastro e mensagens de erro da autenticação.
- Android Lint sem erros; há avisos de atualização de dependências e recomendações.
- APK release 1.0.0/10000 compilado e assinatura APK v2 validada por `apksigner`.
- APK release 1.0.1/10001 compilado e assinatura verificada, com conexão Supabase pública pré-configurada; o esquema Room permanece na versão 1.
- Repositório público criado, código enviado e pré-release 1.0.0 publicada com APK e SHA-256.
- Quatro Actions Secrets de assinatura configurados de forma criptografada; chave local fora do checkout.
- Migração aplicada ao projeto Supabase `igehbjnutmhkqfdlrndx`: oito tabelas com RLS e três tabelas publicadas no Realtime.
- Script SQL de aceite executado com sucesso no Supabase: idempotência, merges, conflitos, marcação de compra, viewer, acesso revogado, convites e tombstones. Rollback confirmado: zero usuários temporários e zero listas de teste restantes.
- Auth por e-mail habilitado, confirmação de e-mail mantida e chave publishable configurada no app. Nenhuma chave administrativa no APK.
- Corrigida a causa da ausência de atualização: releases estavam marcadas como pré-release e `/releases/latest` retornava 404. A 1.0.1 foi disponibilizada no canal normal; o workflow foi corrigido para publicar releases completas nesse canal.
- Versão 1.0.2 acrescenta aviso de atualização acionável na abertura/retorno ao app e cache da versão encontrada. A confirmação do instalador Android continua obrigatória.
- Versão 1.0.3 simplifica a tela inicial com inclusão rápida, itens pendentes em cima e comprados embaixo, preservando as listas existentes. Compilação debug, 32 testes unitários e Lint concluídos sem erros; validação visual em celular ainda pendente.
- Versão 1.0.4 adiciona identidade visual: ícone adaptativo Android com variante monocromática, tela de abertura, paleta verde/creme/dourado e cartões na tela de compras. A validação em celular continua pendente.
- Versão 1.0.5 agrupa os produtos ainda necessários acima dos selecionados, apresenta botão fixo **Comprados** após a seleção e cria uma aba de histórico limitada às 10 compras mais recentes. Uma migração Room 1→2 preserva itens e listas existentes. Histórico local é incluído em backups JSON v2 e restaura arquivos v1.
- Versão 1.0.6 simplifica a tela **Conta e ajustes** em seções, dá acesso direto à conta pelo menu da lista e separa cadastro e login. Valida e confirma senhas, informa a etapa de confirmação por e-mail e interpreta erros da API Supabase (credenciais, e-mail inválido/não confirmado, limite de tentativas, cadastro desabilitado e conexão). Não há teste real de criação de conta concluído no servidor; configuração SMTP e limites de e-mail permanecem dependências externas.
- Versão 1.0.7 corrige o tratamento do cadastro com confirmação por e-mail: o Supabase pode responder com o usuário diretamente na raiz da resposta (sem `access_token`), e o aplicativo agora reconhece esse formato como cadastro recebido. Testes cobrem o retorno realista e respostas inválidas. O envio efetivo do e-mail continua dependente das configurações do Supabase.
- Versão 1.0.8 renova exclusivamente o visual: paleta clara/escura, resumo compacto de compras, produtos mais legíveis, campo de inclusão refinado, histórico harmonizado e seções de ajustes com ícones. Fluxos, dados e sincronização permanecem iguais. Compilação e testes locais aprovados; validação visual em aparelho pendente.
- Versão 1.0.9 remove o cartão de progresso "Faltam produtos", simplifica o cabeçalho e redesenha a marca como uma sacola com monograma M, com variantes adaptativa e monocromática. As funções da lista e de sincronização permanecem iguais. Validação visual em aparelho pendente.

## Implementado, com validação externa pendente

- Banco Room e transações, CRUD de listas/produtos, temas, modo de compras, categorias, orçamento, estatísticas e backup.
- Supabase Auth REST, refresh de sessão, criptografia AndroidKeyStore, Realtime WebSocket, fila local, reconexão, RPC de conflitos, RLS, convites e auditoria.
- Atualizador: consulta pública, download, checksum, pacote, assinatura, versionCode e PackageInstaller.
- Leitor por Google Play Services e consulta Open Food Facts, com cadastro manual.
- Lembretes e checagem de updates via WorkManager.
- Workflow de release assinado: Secrets configurados; execução em GitHub Actions em validação.

## Ainda necessário para cumprir integralmente o escopo

- Executar a matriz abaixo em dois aparelhos usando o Supabase já configurado; os testes das RPCs e RLS no banco já passaram.
- Executar testes instrumentados em dispositivo/emulador; o ambiente não tem aparelho conectado.
- Verificar atualização 1.0.0 → 1.1.0 assinada, com preservação do banco, sessão e fila.
- Testes instrumentados específicos de migração de esquema, testes de download interrompido/corrompido e assinatura incompatível.
- Cursor incremental durável, assinaturas por lista e tratamento dedicado de remoção de acesso com fila offline.
- Histórico financeiro/estatísticas completas por categoria e preços históricos; a aba de compras pagas já usa snapshots imutáveis locais, mas as estatísticas legadas ainda dependem de listas concluídas e produtos persistidos.
- Catálogo Product independente, gerenciamento completo de favoritos, ordenação manual e opções de ordenação persistidas.
- Edição de categorias (nome/ícone/cor), importação/exportação completa incluindo categorias/preferências e limpeza de histórico com confirmação.
- Links de convite/deep links, nomes e avatares dos participantes em vez de UUIDs abreviados, horário de compra e notificações específicas de alterações compartilhadas.
- Fluxo nativo completo de recuperação de senha; atualmente é enviado o e-mail para o fluxo web configurado.
- Revisão visual em aparelhos pequenos, telas grandes, modo escuro e com escala de fonte elevada.
- Atualizar toolchain para o SDK estável mais recente após instalar/validar o ambiente correspondente.
- Teste de segurança Strix só depois de existir um backend configurado e um alvo autorizado. O conector Code Review disponível consulta PRs; não há PR de revisão criado. Graphify não indexa este repositório novo.

## Matriz de aceite de integração

Cada cenário deve produzir evidência em dispositivo e no servidor antes de declarar sincronização pronta:

1. Dois usuários adicionam produtos à mesma lista.
2. Marcação de compra aparece no outro aparelho sem atualização manual.
3. Preço e quantidade editados simultaneamente são preservados.
4. Mesmo campo editado gera conflito e a decisão explícita é aplicada.
5. Compra offline persiste após fechar e reabrir o app.
6. Reconexão após horas e token expirado reaplica a fila.
7. Mesmo UUID de operação não duplica dados nem auditoria.
8. Convite expirado é rejeitado.
9. Convite revogado é rejeitado.
10. Visualizador não consegue editar por RPC nem REST direto.
11. Participante removido não consegue ler/escrever no servidor.
12. Exclusão offline permanece excluída após reconexão de outro aparelho.
13. Atualização de APK preserva alterações pendentes e sessão.
14. Migração de banco preserva listas, itens e fila.

## Riscos conhecidos

Realtime ainda usa snapshots paginados, com reconciliação periódica. As RPCs e políticas foram testadas em um projeto Supabase real; o fluxo Android entre dois aparelhos e o WebSocket ainda precisam de validação. Backups preservam registros existentes, mas não substituem um backup integral do estado de sincronização. A assinatura do APK protege a cadeia de atualização; guarde a chave e as senhas fora do GitHub.
