# Política de Privacidade - Finly 🔒

**Última atualização:** 25 de Fevereiro de 2026

A sua privacidade é fundamental para nós. Esta Política de Privacidade explica como o aplicativo **Finly** ("nós", "nosso") recolhe, utiliza, armazena e protege os dados dos utilizadores ("você", "utilizador"), em conformidade com o Regulamento Geral sobre a Proteção de Dados (**RGPD / GDPR**) da União Europeia e a Lei Geral de Proteção de Dados (**LGPD**) do Brasil.

---

## 1. Informações que Recolhemos

Para prestar os serviços de gestão financeira pessoal e sincronização em nuvem, o Finly recolhe apenas as informações estritamente necessárias:

* **Informações de Conta:** Endereço de e-mail e nome de exibição fornecidos no momento do registo ou autenticação via Google Firebase Auth.
* **Dados Financeiros Pessoais:** Transações registadas pelo utilizador (rendas, despesas, categorias, datas de vencimento, valores e metas de poupança).
* **Preferências do Aplicativo:** Moeda selecionada (€ EUR ou R$ BRL), modo de tema (Escuro/Claro), configurações de notificações e estado de ativação da biometria.
* **Modo Convidado:** Se optar por utilizar a aplicação no "Modo Convidado", nenhum dado pessoal ou financeiro é enviado para os nossos servidores na nuvem; todos os dados permanecem guardados exclusivamente na memória local do seu telemóvel (Room DB).

---

## 2. Como Utilizamos as Suas Informações

As informações recolhidas são utilizadas exclusivamente para as seguintes finalidades:

* Permitir o acesso seguro à sua conta e sincronizar as suas finanças pessoais em tempo real entre os seus dispositivos.
* Calcular saldos, estatísticas, gráficos de despesas e projeções financeiras.
* Agendar e enviar lembretes locais no seu dispositivo sobre contas próximas do vencimento.
* Processar a gestão de subscrições e funcionalidades premium através da Google Play Billing API.

---

## 3. Armazenamento e Segurança dos Dados

* **Armazenamento na Nuvem:** Os dados da sua conta são armazenados de forma encriptada na infraestrutura de nuvem do **Google Firebase Firestore** (região `europe-west3`).
* **Segurança em Trânsito:** Toda a comunicação entre o aplicativo e os nossos servidores é protegida através de protocolos encriptados HTTPS/TLS.
* **Proteção Biométrica:** O aplicativo suporta autenticação por impressão digital ou PIN local via `BiometricPrompt` para impedir acessos não autorizados no seu telemóvel.

---

## 4. Partilha de Dados com Terceiros

**NÃO vendemos, alugamos ou comercializamos os seus dados pessoais ou financeiros com terceiros.**

Partilhamos dados apenas com fornecedores de infraestrutura essenciais para o funcionamento da aplicação:
* **Google Firebase:** Autenticação de utilizadores e base de dados encriptada na nuvem.
* **Google Play Services / Google Play Billing:** Processamento seguro de pagamentos e gestão de subscrições.

---

## 5. Eliminação de Dados e Seus Direitos (RGPD / LGPD)

Você tem o controlo total sobre os seus dados pessoais. A qualquer momento, pode exercer os seguintes direitos:

* **Direito de Eliminação (Direito ao Esquecimento):** Pode apagar permanentemente a sua conta e todos os dados associados (transações, metas e preferências) diretamente no aplicativo, acedendo a **Perfil > Excluir Conta (Ação Irreversível)**. Essa ação elimina instantaneamente todos os seus registos da nossa base de dados na nuvem e do seu telemóvel.
* **Acesso e Exportação:** Pode exportar os seus relatórios e cópias de segurança a qualquer momento em formato PDF, CSV ou ficheiro encriptado `.finly`.

---

## 6. Alterações a Esta Política de Privacidade

Podemos atualizar esta Política de Privacidade periodicamente para refletir melhorias no aplicativo ou alterações legislativas. Recomendamos a consulta desta página regularmente.

---

## 7. Contacto

Se tiver dúvidas, questões ou pretender exercer os seus direitos de privacidade, entre em contacto connosco através do e-mail:

* **E-mail de Suporte:** `jesserodrigues@gmail.com`
* **Desenvolvedor:** Jesse Rodrigues
* **Aplicação:** Finly - Gestão Financeira Inteligente
