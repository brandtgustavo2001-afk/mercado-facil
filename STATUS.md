# Estado da entrega

Este projeto é uma primeira implementação funcional, **não a conclusão integral dos dois briefings**.

## Verificado no ambiente local

- Compilação do APK debug e do APK de testes de instrumentação.
- Vinte testes automatizados: centavos/quantidades, comparação SemVer, patch de sincronização, URLs de atualização, quatro testes reais de Room/SQLite em Robolectric e configuração automática do backend após atualizar uma instalação antiga.
- Android Lint sem erros; há avisos de atualização de dependências e recomendações.
- APK release 1.0.0/10000 compilado e assinatura APK v2 validada por `apksigner`.
- APK release 1.0.1/10001 compilado e assinatura verificada, com conexão Supabase pública pré-configurada; o esquema Room permanece na versão 1.
- Repositório público criado, código enviado e pré-release 1.0.0 publicada com APK e SHA-256.
- Quatro Actions Secrets de assinatura configurados de forma criptografada; chave local fora do checkout.
- Migração aplicada ao projeto Supabase `igehbjnutmhkqfdlrndx`: oito tabelas com RLS e três tabelas publicadas no Realtime.
- Script SQL de aceite executado com sucesso no Supabase: idempotência, merges, conflitos, marcação de compra, viewer, acesso revogado, convites e tombstones. Rollback confirmado: zero usuários temporários e zero listas de teste restantes.
- Auth por e-mail habilitado, confirmação de e-mail mantida e chave publishable configurada no app. Nenhuma chave administrativa no APK.

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
- Histórico financeiro e de preços em entidades próprias e snapshots imutáveis; hoje os relatórios usam listas concluídas e produtos persistidos.
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
