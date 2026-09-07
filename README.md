# Finly - Gestão Financeira Inteligente 🚀💰

**Finly** é uma aplicação Android moderna, segura e intuitiva desenhada para ajudar utilizadores a ter um controlo total sobre as suas finanças pessoais. Com um design premium no padrão Material Design 3, suporte multi-idioma nativo e funcionalidades avançadas de sincronização em nuvem, o Finly transforma a gestão de gastos numa experiência simples e organizada.

---

## ✨ Funcionalidades Principais

*   **🌐 Suporte Multi-Idioma Nativo (Per-App Language Preferences):** Suporte completo para 4 variantes linguísticas (`Português (Portugal)`, `Português (Brasil)`, `English (US)` e `Español`). Troca dinâmica de idioma em 1 toque no Perfil com integração nativa às preferências de linguagem do Android 13+. Toda a interface, categorias, meses, relatórios (PDF/CSV) e notificações adaptam-se instantaneamente.
*   **🌍 Sistema Multi-Moeda Dinâmico & Onboarding de Boas-Vindas:** Modal de acolhimento inicial para escolha da moeda principal (`€ EUR` / `R$ BRL`), alternância em 1 toque no perfil, máscara automática de centavos durante a digitação (`MoneyTextWatcher`), suporte em todos os ecrãs, gráficos e relatórios (PDF/CSV) e sincronização instantânea em tempo real entre dispositivos.
*   **📊 Dashboards Visuais & Inteligência Financeira:** Gráficos circulares (Donut) para resumo mensal, alertas visuais de teto orçamental quando despesas superam 100% da renda, badges dinâmicas de variação vs. mês anterior (`↓ X%` em verde ou `↑ X%` em vermelho), e card dedicado de **Previsão de Fim de Mês** (`🟢 No Verde` / `🔴 No Vermelho`).
*   **📅 Seleção em Grelha Rígida 4x3 & Sincronização Bidirecional:** Modal flutuante de seleção rápida em grelha 4x3 (`JAN`..`DEZ`) e seletor horizontal de anos com cálculo dinâmico baseado no histórico. Sincronização bidirecional instantânea do período ativo entre o **Resumo Mensal** e a **Listagem Detalhada / Extrato**.
*   **🔔 Notificações Inteligentes de Vencimento:** Alertas automáticos diários às 09:00 AM via **WorkManager** sobre contas a vencer (hoje, amanhã, nos próximos dias ou em atraso recente), com ações diretas de "Marcar como Paga" e "Ver Detalhes" na própria notificação.
*   **📤 Exportação Multiformato (PDF & CSV / Excel):** Modal no menu lateral com geração de relatórios em PDF (vetorial com gráficos e tabelas) e CSV (estruturado com codificação UTF-8 BOM e separador `;` para Microsoft Excel e Google Sheets/Planilhas) adaptados à moeda e idioma ativos, guardados na pasta **Downloads**.
*   **🔄 Sincronização em Tempo Real:** Integração total com **Firebase Firestore**, garantindo que transações, metas de poupança, moeda ativa e categorias personalizadas sejam atualizadas instantaneamente em tempo real entre todos os dispositivos ligados à mesma conta.
*   **🏷️ Gestão de Categorias & Ícones Dinâmicos:** Crie e gira as suas próprias categorias. O seletor de categoria herda automaticamente o ícone correspondente à categoria selecionada. As 12 categorias padrão do sistema são automaticamente traduzidas para o idioma ativo da interface.
*   **🔐 Segurança de Nível Bancário & Modo Convidado Privado:** Autenticação e gestão de credenciais 100% encriptadas via **Firebase Auth** (sem armazenamento de senhas em texto limpo no Firestore). O modo "Convidado" guarda dados exclusivamente locais (Room DB), sem qualquer envio para a nuvem.
*   **👆 Bloqueio por Biometria / Impressão Digital:** Proteção da aplicação através de impressão digital ou PIN com a API nativa `BiometricPrompt`. As preferências de biometria são sincronizadas com o perfil do utilizador.
*   **🔄 Recorrência Inteligente & Confirmação de Escopo:** Registe contas fixas ("Repetir Sempre") ou parceladas. Ao editar ou eliminar uma conta recorrente, escolha se deseja aplicar a alteração apenas ao mês selecionado ou a este mês e todos os meses futuros.
*   **📈 Evolução Anual:** Painel exclusivo para comparar o desempenho financeiro mês a mês ao longo do ano com gráfico de barras e balanço consolidado.
*   **🌓 Modo Escuro Nativo:** Interface totalmente adaptada para os modos Light e Dark com alteração fluida de tema.
*   **📱 UX & Teclado Fluido:** Seletor de tipo de transação com botões em toggle (`MaterialButtonToggleGroup`), ajuste dinâmico ao abrir o teclado (`adjustResize`), foco automático e ação de conclusão rápida.

---

## 📸 Demonstração Visual (Screenshots)

### 🔐 Autenticação & Entrada
| Login | Registo de Conta | Modo Convidado |
| :---: | :---: | :---: |
| <img src="screenshots/01_login.png" width="220"/> | <img src="screenshots/02_registo.png" width="220"/> | <img src="screenshots/03_aviso_convidado.png" width="220"/> |

### 📝 Gestão & Adição de Transações
| Listagem Detalhada / Extrato | Nova Transação | Detalhes da Transação |
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
*   **Backend & Nuvem:** [Firebase](https://firebase.google.com/) (Firestore em tempo real, Firebase Authentication).
*   **Internacionalização:** Jetpack AppCompat Per-App Language Preferences (`LocaleListCompat`, `locales_config.xml`).
*   **Notificações & Background:** [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) para agendamento de tarefas e notificações nativas.
*   **Segurança & Biometria:** [AndroidX Biometric API](https://developer.android.com/training/sign-in/biometric-auth) para autenticação por impressão digital e PIN.
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
