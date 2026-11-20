# Nosso Universo Launcher — Projeto Nativo AndroidIDE

Launcher Android independente com identidade visual futurista e **Núcleo HILM** (Hagalel, Íris, Luna, Maya), idealizado por **Luna**.

## Ambiente e Versões Configuradas
- **Android Gradle Plugin (AGP):** `8.2.2`
- **Gradle Wrapper:** `8.2` (`gradle/wrapper/gradle-wrapper.properties`)
- **JDK:** `17`
- **Compile SDK / Target SDK:** `33` (Android 13)
- **Minimum SDK:** `23` (Android 6.0)

## Fidelidade Visual Implementada
- **Barra de status nativa do Android:** preservada com `windowDrawsSystemBarBackgrounds="true"` e `fitsSystemWindows="true"`.
- **Relógio superior grande removido:** mantendo apenas a moldura HUD superior e o alinhamento espacial limpo.
- **Núcleo HILM:** `HilmCoreView` renderiza `res/drawable-nodpi/nucleo_hilm.png` com máscara radial suave diretamente no centro da composição HUD, sem criar outra esfera ao redor.
- **Transparência sobre o papel de parede:** configurado com `android:windowShowWallpaper="true"` e fundo transparente para exibir o papel de parede do aparelho através dos painéis.

## Como Abrir no AndroidIDE
1. Extraia o arquivo `NossoUniversoLauncher-AndroidIDE.zip`.
2. Abra a pasta `NossoUniversoLauncher` no **AndroidIDE**.
3. Certifique-se de que o AndroidIDE está usando **JDK 17** e que o Gradle Wrapper (`gradle/wrapper/gradle-wrapper.properties`) aponta para **Gradle 8.2**.
4. Sincronize o Gradle e compile com **Build > Build APK** (`assembleDebug`).
5. Instale no dispositivo, pressione o botão **Home** e selecione **Nosso Universo** (ou segure o ícone central para abrir as Configurações e definir como Launcher Padrão).
