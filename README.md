# Finly - Gestão Financeira Inteligente 🚀💰

**Finly** é uma aplicação Android moderna, segura e intuitiva desenhada para ajudar utilizadores a ter um controlo total sobre as suas finanças pessoais. Com um design premium no padrão Material Design 3 e funcionalidades avançadas de sincronização em nuvem, o Finly transforma a gestão de gastos numa experiência simples e organizada.

---

## ✨ Funcionalidades Principais

*   **📊 Dashboards Visuais & Inteligência Financeira:** Gráficos circulares (Donut) para resumo mensal, alertas visuais de teto orçamental quando despesas superam 100% da renda, badges dinâmicas de variação vs. mês anterior (`↓ X%` em verde ou `↑ X%` em vermelho), e card dedicado de **Previsão de Fim de Mês** (`🟢 No Verde` / `🔴 No Vermelho`).
*   **📅 Seleção em Grelha Rígida 4x3 & Sincronização Bidirecional:** Modal flutuante de seleção rápida em grelha 4x3 (`JAN`..`DEZ`) e seletor horizontal de anos com cálculo dinâmico baseado no histórico. Sincronização bidirecional instantânea do período ativo entre o **Resumo Mensal** e a **Listagem Detalhada**.
*   **🔔 Notificações Inteligentes de Vencimento:** Alertas automáticos diários às 09:00 AM via **WorkManager** sobre contas a vencer (hoje, amanhã, nos próximos 2 dias ou em atraso recente).
*   **📤 Exportação Multiformato (PDF & CSV / Excel):** Modal no menu lateral com geração de relatórios em PDF (vetorial com gráficos) e CSV (estruturado com codificação UTF-8 BOM e separador europeu `;` pronto para Microsoft Excel e Google Sheets), guardados diretamente na pasta **Downloads**.
*   **🐷 Ícone Oficial de Poupança & Formatação Neutra:** Categoria Poupança identificada com o novo vetor de moeda/cofre ([ic_poupanca.xml](file:///C:/Users/jesse/AndroidStudioProjects/Aplicacao%20Orcamento%20Mesal%20Simples/app/src/main/res/drawable/ic_poupanca.xml)) e valores zerados ($0,00\ €$) formatados de forma neutra sem sinais.
*   **🔄 Sincronização em Tempo Real:** Integração total com **Firebase Firestore**, garantindo que transações, metas de poupança e categorias personalizadas sejam atualizadas instantaneamente em tempo real entre todos os telemóveis ligados à mesma conta.
*   **🏷️ Gestão de Categorias & Ícones Dinâmicos:** Crie e gira as suas próprias categorias. O seletor de categoria herda automaticamente o ícone correspondente à categoria selecionada. Ao eliminar uma categoria em utilização, as transações associadas são reatribuídas em segurança para a categoria **"Geral"**.
*   **🔐 Autenticação & Modo Convidado 100% Privado:** Sistema de login/registo via Firebase Auth. O modo "Convidado" guarda dados exclusivamente locais (Room DB), sem qualquer envio para a nuvem.
*   **👆 Bloqueio por Biometria / Impressão Digital:** Proteção da aplicação através de impressão digital ou PIN com a API nativa `BiometricPrompt`. As preferências de biometria são guardadas no perfil do utilizador no **Firebase Firestore** e **Room DB**, sendo restauradas automaticamente ao iniciar sessão em qualquer dispositivo.
*   **📅 Recorrência Inteligente & Switch Unificado:** Registe contas fixas ("Repetir Sempre") ou parceladas com o novo switch de recorrência intuitivo e ícone de ciclo dedicado.
*   **💶 Símbolo de Moeda Oficial (€):** Campos monetários atualizados com o vetor oficial do Euro (€) e ícones de investimento dedicados.
*   **🔙 Navegação Otimizada:** Botão de voltar dedicado na listagem detalhada de transações para fácil retorno ao resumo mensal.
*   **💡 Inteligência & Insights Financeiros:** Alertas visuais de teto orçamental quando despesas superam 100% da renda, badges dinâmicas de variação vs. mês anterior (`↓ X%` em verde ou `↑ X%` em vermelho), e card dedicado de **Previsão de Fim de Mês** (`🟢 No Verde` / `🔴 Risco de Vermelho`).
*   **📤 Exportação Multiformato (PDF & CSV / Excel):** Modal no menu lateral com seleção de relatório em PDF (vetorial com gráficos) ou CSV (tabela estruturada pronta para Microsoft Excel e Google Sheets com codificação UTF-8 BOM e separador europeu `;`).
*   **📈 Evolução Anual:** Painel exclusivo para comparar o desempenho financeiro mês a mês ao longo do ano.
*   **🌓 Modo Escuro Nativo:** Interface totalmente adaptada para os modos Light e Dark com alteração fluida de tema.
*   **📱 UX & Teclado Fluido:** Seletor de tipo de transação com botões em toggle (`MaterialButtonToggleGroup`), ajuste dinâmico ao abrir o teclado (`adjustResize`), foco automático e ação de conclusão rápida.

---

## 📸 Demonstração Visual (Screenshots)

### 🔐 Autenticação & Entrada
| Login | Registo de Conta | Modo Convidado |
| :---: | :---: | :---: |
| <img src="screenshots/01_login.png" width="220"/> | <img src="screenshots/02_registo.png" width="220"/> | <img src="screenshots/03_aviso_convidado.png" width="220"/> |

### 📝 Gestão & Adição de Transações
| Listagem Detalhada | Nova Transação | Detalhes da Transação |
| :---: | :---: | :---: |
| <img src="screenshots/12_listagem.png" width="220"/> | <img src="screenshots/13_nova_transacao.png" width="220"/> | <img src="screenshots/14_detalhes_item.png" width="220"/> |

### ⚙️ Edição & Eliminação Inteligente
| Editar Item | Eliminar Item | Eliminar Recorrência |
| :---: | :---: | :---: |
| <img src="screenshots/15_editar_item.png" width="220"/> | <img src="screenshots/16_dialog_eliminar_item.png" width="220"/> | <img src="screenshots/17_dialog_eliminar_recorrencia.png" width="220"/> |

### 📊 Dashboards & Relatórios
| Resumo com Gráfico | Lista por Categorias | Evolução Anual (Barras) |
| :---: | :---: | :---: |
| <img src="screenshots/08_resumo_grafico.png" width="220"/> | <img src="screenshots/10_resumo_categorias.png" width="220"/> | <img src="screenshots/09_evolucao_grafico.png" width="220"/> |

### 📖 Perfil & Guia do Utilizador
| O Meu Perfil | Editar Perfil | Guia do Utilizador |
| :---: | :---: | :---: |
| <img src="screenshots/06_perfil.png" width="220"/> | <img src="screenshots/07_editar_perfil.png" width="220"/> | <img src="screenshots/11_guia_utilizador.png" width="220"/> |

---

## 🛠️ Tecnologias Utilizadas

O projeto foi construído com as melhores práticas de desenvolvimento Android:

*   **Linguagem:** [Kotlin](https://kotlinlang.org/) (100% Nativo)
*   **Arquitetura:** MVVM (Model-View-ViewModel) com Coroutines & Flow.
*   **Base de Dados Local:** [Room Database](https://developer.android.com/training/data-storage/room) para persistência offline rápida.
*   **Backend & Nuvem:** [Firebase](https://firebase.google.com/) (Firestore em tempo real, Auth).
*   **Notificações & Background:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) para agendamento de tarefas e notificações nativas.
*   **Segurança & Biometria:** [AndroidX Biometric API](https://developer.android.com/training/sign-in/biometric-auth) para autenticação segura por impressão digital e PIN.
*   **Gráficos & PDFs:** [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) e API nativa `PdfDocument` com renderização vetorial de alta definição.
*   **UI/UX:** Material Design 3, View Binding, Edge-to-Edge API, Inset Listeners.

---

## 🚀 Como Executar o Projeto

1.  Clone este repositório:
    ```bash
    git clone https://github.com/Jrodrygues/Finly-Gestao-Financeira.git
    ```
2.  Abra o projeto no **Android Studio**.
3.  Configure o seu ficheiro `google-services.json` do Firebase na pasta `app/`.
4.  Sincronize o Gradle e execute no seu emulador ou dispositivo físico.

---

## 📄 Licença

Este projeto está sob a licença MIT - veja o ficheiro [LICENSE](LICENSE) para detalhes.

---

## ✍️ Autor

Desenvolvido com ❤️ por **Jesse Rodrigues**. 

---
*Finly - O seu futuro financeiro começa aqui.*
