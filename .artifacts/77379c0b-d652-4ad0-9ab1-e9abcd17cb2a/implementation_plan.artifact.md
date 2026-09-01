# Plano de Implementação - Ajustes de Layout e Lógica de Saldo

Este plano detalha as correções solicitadas antes da integração com o Firebase, focando na organização das transações, cálculo de saldo baseado em status de pagamento e ajustes de espaçamento visual.

## Alterações Propostas

### 1. Organização por Data de Vencimento
As listas de transações na `MainActivity` serão ordenadas cronologicamente (do dia 1 ao dia 31).

### 2. Lógica de Saldo (Apenas Pagos)
Tanto na `MainActivity` quanto na `ResumoActivity`, os totais de Renda, Despesa e Saldo Final passarão a considerar apenas as transações marcadas como **Pagas** (status = true).

### 3. Ajustes de Espaçamento e Rótulos
Adição de espaço e dois pontos (:) nos rótulos de totais na `ResumoActivity` e garantia de espaçamento na `MainActivity`.

## Arquivos a Modificar

### [MODIFY] [MainActivity.kt](file:///C:/Users/jesse/AndroidStudioProjects/Aplicacao%20Orcamento%20Mesal%20Simples/app/src/main/java/com/jesse/finly/MainActivity.kt)
- Modificar `filtrarLista` para adicionar a ordenação por vencimento.
- Modificar `atualizarSaldoVisual` para filtrar por `it.status`.

### [MODIFY] [ResumoActivity.kt](file:///C:/Users/jesse/AndroidStudioProjects/Aplicacao%20Orcamento%20Mesal%20Simples/app/src/main/java/com/jesse/finly/ResumoActivity.kt)
- Modificar `carregarDados` para filtrar transações por `it.status` nos cálculos de totais.

### [MODIFY] [strings.xml](file:///C:/Users/jesse/AndroidStudioProjects/Aplicacao%20Orcamento%20Mesal%20Simples/app/src/main/res/values/strings.xml)
- Atualizar `label_total_renda`, `label_total_despesas` e `label_saldo` para incluir ": " ao final, garantindo o espaçamento solicitado.

## Plano de Verificação

### Testes Manuais
1. **Ordenação**: Abrir a listagem (Home) e verificar se os itens estão em ordem crescente de dia de vencimento.
2. **Saldo Dinâmico**: Marcar uma despesa como paga e verificar se o saldo diminui. Desmarcar e verificar se volta ao valor anterior.
3. **Visual**: Conferir se os rótulos de saldo e totais possuem o espaço solicitado (ex: `Saldo Final: 100.00 €`).
