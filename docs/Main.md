# Pico-HSM Java PoC


Classe Main.java

Proof of Concept de integração entre uma aplicação Java e um HSM baseado em **Raspberry Pi Pico RP2040**, utilizando **Pico-HSM, OpenSC, PKCS#11 e SunPKCS11**.

O objetivo desta aplicação é validar os principais conceitos necessários para integrar uma aplicação Java com um HSM corporativo real, utilizando o Raspberry Pi Pico como dispositivo de laboratório.

> **Importante:** esta aplicação é uma prova de conceito. O Raspberry Pi Pico RP2040 e o Pico-HSM utilizado neste projeto não devem ser considerados substitutos de um HSM corporativo certificado.

---

## 1. Objetivo

A aplicação demonstra como uma aplicação Java pode acessar uma chave privada armazenada em um HSM através da interface padrão **PKCS#11**.

A arquitetura utilizada é:

```text
Java Application
       │
       ▼
JCA / JCE
       │
       ▼
SunPKCS11
       │
       ▼
OpenSC PKCS#11
       │
       ▼
USB / CCID / PCSC
       │
       ▼
Pico-HSM
       │
       ▼
Raspberry Pi Pico RP2040
```

A chave privada RSA é criada dentro do Pico-HSM e configurada como:

```text
sensitive
always sensitive
never extractable
local
```

Portanto, a aplicação Java não recebe os bytes da chave privada.

O Java manipula apenas uma referência PKCS#11 para a chave armazenada no dispositivo.

---

# 2. O que a aplicação faz

A aplicação implementa dois comandos:

```text
generate
verify
```

## Generate

Recebe uma senha como argumento e solicita ao Pico-HSM que realize uma assinatura utilizando:

```text
SHA-512 + RSA PKCS#1 v1.5
```

Exemplo:

```cmd
java Main generate MinhaSenha123!
```

Fluxo:

```text
password
   │
   ▼
SHA-512
   │
   ▼
RSA Signature
   │
   ▼
Private Key
(Pico-HSM)
   │
   ▼
Signature
   │
   ▼
Base64
```

O resultado é algo semelhante a:

```text
SIGNATURE_BASE64=ZtXGkS0g7...
```

A chave privada permanece dentro do HSM durante toda a operação.

---

## Verify

Recebe:

```text
password
+
signature Base64
```

e verifica se a assinatura corresponde à senha utilizando a chave pública associada.

Exemplo:

```cmd
java Main verify MinhaSenha123! ZtXGkS0g7...
```

Resultado esperado:

```text
MATCH
```

Utilizando uma senha diferente:

```cmd
java Main verify SenhaErrada ZtXGkS0g7...
```

o resultado será:

```text
NO_MATCH
```

O fluxo completo da PoC é:

```text
                 GENERATE

password
   │
   ▼
SHA-512
   │
   ▼
RSA PKCS#1 v1.5
   │
   ▼
Private Key ID 01
   │
   │ Pico-HSM
   ▼
Signature
   │
   ▼
Base64


                  VERIFY

password + signature
        │
        ▼
SHA512withRSA verify
        │
        ▼
Public Key
        │
        ├── valid   → MATCH
        │
        └── invalid → NO_MATCH
```

---

# 3. Hardware utilizado

A PoC foi validada utilizando:

* Raspberry Pi Pico RP2040
* Firmware Pico-HSM
* Interface USB CCID
* SmartCard-HSM 6.6

O dispositivo é identificado pelo Windows/OpenSC como:

```text
Pol Henarejos Pico Key CCID Interface 0
```

e:

```text
SmartCard-HSM version 6.6
```

O token PKCS#11 utilizado durante os testes apresentou:

```text
token label        : Pico-HSM
token manufacturer : Pol Henarejos
token model        : PKCS#15 emulated
token flags        : login required, rng, token initialized, PIN initialized
firmware version   : 6.6
```

---

# 4. O que você precisa para começar

## Hardware

* Raspberry Pi Pico RP2040
* Firmware Pico-HSM instalado no dispositivo

Projeto Pico-HSM:

https://github.com/polhenarejos/pico-hsm

## Software

A PoC foi desenvolvida/testada utilizando:

* Windows
* OpenSC
* Java 25
* OpenSSL
* Pico-HSM firmware 6.6

### OpenSC

O OpenSC fornece:

```text
opensc-tool
pkcs11-tool
pkcs15-tool
sc-hsm-tool
```

Além da biblioteca PKCS#11 utilizada pelo Java:

```text
opensc-pkcs11.dll
```

Uma instalação típica no Windows utiliza:

```text
C:\Program Files\OpenSC Project\OpenSC\
```

### Java

O ambiente utilizado durante a PoC foi:

```text
Java 25.0.4.1 LTS
```

Verifique:

```cmd
java -version
```

Também é necessário que o JDK possua:

```text
jdk.crypto.cryptoki
```

Verifique com:

```cmd
java --list-modules | findstr cryptoki
```

Resultado esperado:

```text
jdk.crypto.cryptoki
```

Esse módulo fornece o suporte necessário ao `SunPKCS11`.

### OpenSSL

OpenSSL foi utilizado para criação e validação do certificado X.509 da PoC.

A versão utilizada durante os testes foi:

```text
OpenSSL 4.0.2
```

---

# 5. Setup

## 5.1 Verificar o dispositivo

Conecte o Pico-HSM ao computador.

Execute:

```cmd
opensc-tool -l
```

Resultado esperado:

```text
# Detected readers (pcsc)

0  Yes  Pol Henarejos Pico Key CCID Interface 0
```

Verifique o dispositivo:

```cmd
opensc-tool -n
```

Resultado esperado:

```text
SmartCard-HSM version 6.6
```

---

# 6. Inicialização do Pico-HSM

> **ATENÇÃO**
>
> Inicializar um SmartCard-HSM pode apagar chaves e certificados existentes.
>
> Não execute este procedimento em um dispositivo contendo chaves que precisem ser preservadas.

Inicialização:

```cmd
sc-hsm-tool --initialize --so-pin <SO-PIN> --pin <USER-PIN>
```

O comando configura duas credenciais.

### SO-PIN

Security Officer PIN.

Utilizado para operações administrativas e de recuperação.

### User PIN

PIN utilizado pela aplicação para acessar operações protegidas do HSM.

Depois da inicialização:

```cmd
pkcs11-tool -L
```

deve apresentar:

```text
token flags:
    login required
    rng
    token initialized
    PIN initialized
```

Teste o login:

```cmd
pkcs11-tool --login -O
```

O token deverá solicitar:

```text
Please enter User PIN:
```

---

# 7. Gerando o par de chaves RSA

Para esta PoC utilizamos RSA 2048 bits.

Execute:

```cmd
pkcs11-tool --login ^
  --keypairgen ^
  --key-type rsa:2048 ^
  --id 01 ^
  --label "MinhaChave"
```

A chave privada é criada **dentro do HSM**.

Liste os objetos:

```cmd
pkcs11-tool --login -O
```

O resultado deverá conter:

```text
Private Key Object; RSA 2048 bits
  label: MinhaChave
  ID: 01
  Usage: decrypt, sign
  Access:
      sensitive
      always sensitive
      never extractable
      local
```

e:

```text
Public Key Object; RSA 2048 bits
  label: MinhaChave
  ID: 01
```

A propriedade mais importante é:

```text
never extractable
```

A chave privada não pode ser exportada do HSM.

---

# 8. Testando a assinatura diretamente pelo PKCS#11

Antes de utilizar Java, é recomendado testar o HSM diretamente.

Crie um arquivo:

```cmd
<nul set /p ="MinhaSenha123!" > senha.txt
```

Assine:

```cmd
pkcs11-tool --login ^
  --sign ^
  --mechanism SHA512-RSA-PKCS ^
  --id 01 ^
  --input-file senha.txt ^
  --output-file assinatura.bin
```

Isso executa:

```text
SHA-512
   +
RSA PKCS#1
   +
Private Key ID 01
```

A chave privada permanece no Pico-HSM.

---

# 9. Exportando a chave pública

A chave pública pode ser exportada normalmente:

```cmd
pkcs11-tool ^
  --read-object ^
  --type pubkey ^
  --id 01 ^
  --output-file chavepublicadodevice.der
```

Converta para PEM:

```cmd
openssl pkey ^
  -pubin ^
  -inform DER ^
  -in chavepublicadodevice.der ^
  -out chavepublicadodevice.pem
```

---

# 10. Validando a assinatura

Execute:

```cmd
openssl dgst -sha512 ^
  -verify chavepublicadodevice.pem ^
  -signature assinatura.bin ^
  senha.txt
```

Resultado esperado:

```text
Verified OK
```

Agora altere o conteúdo.

Exemplo:

```text
MinhaSenha124!
```

A validação deverá retornar:

```text
Verification failure
```

Isso comprova o fluxo assimétrico:

```text
Private Key → SIGN
Public Key  → VERIFY
```

---

# 11. Certificado X.509

O Java `KeyStore` PKCS#11 trabalha melhor quando a chave privada está associada a um certificado X.509.

Portanto, precisamos ter:

```text
ID 01
├── Private Key
├── Public Key
└── Certificate
```

Todos utilizando o mesmo identificador PKCS#11.

---

# 12. Criando uma CA para a PoC

Crie uma chave privada temporária para a CA:

```cmd
openssl genpkey ^
  -algorithm RSA ^
  -pkeyopt rsa_keygen_bits:2048 ^
  -out poc-ca-key.pem
```

Crie o certificado da CA:

```cmd
openssl req -new -x509 ^
  -key poc-ca-key.pem ^
  -sha256 ^
  -days 3650 ^
  -subj "/CN=Pico HSM PoC CA" ^
  -out poc-ca.pem
```

> Essa CA existe somente para o laboratório.

---

# 13. Criando o certificado da chave do HSM

Utilize a chave pública exportada do Pico-HSM:

```cmd
openssl x509 -new ^
  -force_pubkey chavepublicadodevice.pem ^
  -CA poc-ca.pem ^
  -CAkey poc-ca-key.pem ^
  -set_serial 1 ^
  -days 3650 ^
  -subj "/CN=Pico HSM Java PoC" ^
  -sha512 ^
  -out cert01.pem
```

Verifique:

```cmd
openssl verify ^
  -CAfile poc-ca.pem ^
  cert01.pem
```

Resultado:

```text
cert01.pem: OK
```

Converta para DER:

```cmd
openssl x509 ^
  -in cert01.pem ^
  -outform DER ^
  -out cert01.der
```

---

# 14. Importando o certificado no HSM

Importe utilizando o mesmo ID da chave:

```cmd
pkcs11-tool --login ^
  --write-object cert01.der ^
  --type cert ^
  --id 01 ^
  --label "MinhaChave"
```

Agora:

```cmd
pkcs11-tool --login -O
```

deverá apresentar:

```text
Private Key
  ID: 01
  label: MinhaChave

Certificate
  ID: 01
  label: MinhaChave

Public Key
  ID: 01
  label: MinhaChave
```

---

# 15. Configuração do Java PKCS#11

Crie:

```text
pkcs11.cfg
```

com:

```properties
name = PicoHSM
library = C:/Program Files/OpenSC Project/OpenSC/pkcs11/opensc-pkcs11.dll
slotListIndex = 0
```

A arquitetura fica:

```text
Java
 │
 ▼
SunPKCS11
 │
 ▼
opensc-pkcs11.dll
 │
 ▼
OpenSC
 │
 ▼
SmartCard-HSM
 │
 ▼
Pico-HSM
```

No Java, o provider é carregado através de:

```java
Provider base =
    Security.getProvider("SunPKCS11");

Provider provider =
    base.configure(
        "C:/pico-hsm/pico-hsm-poc/pkcs11.cfg"
    );

Security.addProvider(provider);
```

---

# 16. Compilação

Exemplo:

```cmd
"%JAVA_HOME%\javac.exe" ^
  -d out ^
  src\Main.java
```

---

# 17. Executando a aplicação

A aplicação possui dois modos:

```text
generate
verify
```

---

## 17.1 Generate

Execute:

```cmd
"%JAVA_HOME%\java.exe" ^
  -cp out ^
  Main generate MinhaSenha123!
```

O Pico-HSM solicitará o PIN:

```text
PIN do Pico-HSM:
```

A aplicação utilizará:

```java
Signature.getInstance(
    "SHA512withRSA",
    pkcs11Provider
);
```

Isso garante que a assinatura seja realizada através do provider PKCS#11.

Resultado:

```text
SIGNATURE_BASE64=...
```

O valor Base64 representa a assinatura RSA.

---

## 17.2 Verify

Execute:

```cmd
"%JAVA_HOME%\java.exe" ^
  -cp out ^
  Main verify MinhaSenha123! <SIGNATURE_BASE64>
```

Resultado esperado:

```text
MATCH
```

Teste uma senha diferente:

```cmd
"%JAVA_HOME%\java.exe" ^
  -cp out ^
  Main verify MinhaSenha124! <SIGNATURE_BASE64>
```

Resultado:

```text
NO_MATCH
```

---

# 18. Como executar os testes

## Teste 1 — dispositivo disponível

```cmd
opensc-tool -l
```

Esperado:

```text
Pol Henarejos Pico Key CCID Interface 0
```

---

## Teste 2 — SmartCard-HSM

```cmd
opensc-tool -n
```

Esperado:

```text
SmartCard-HSM version 6.6
```

---

## Teste 3 — PKCS#11

```cmd
pkcs11-tool -L
```

Esperado:

```text
Pico-HSM
login required
token initialized
PIN initialized
```

---

## Teste 4 — objetos

```cmd
pkcs11-tool --login -O
```

Esperado:

```text
Private Key ID 01
Public Key ID 01
Certificate ID 01
```

---

## Teste 5 — assinatura

```cmd
pkcs11-tool --login ^
  --sign ^
  --mechanism SHA512-RSA-PKCS ^
  --id 01 ^
  --input-file senha.txt ^
  --output-file assinatura.bin
```

---

## Teste 6 — verificação positiva

```cmd
openssl dgst -sha512 ^
  -verify chavepublicadodevice.pem ^
  -signature assinatura.bin ^
  senha.txt
```

Esperado:

```text
Verified OK
```

---

## Teste 7 — verificação negativa

Execute utilizando conteúdo diferente.

Esperado:

```text
Verification failure
```

---

## Teste 8 — Java positivo

```cmd
java Main generate MinhaSenha123!
```

Copie a assinatura retornada.

Depois:

```cmd
java Main verify MinhaSenha123! <signature>
```

Esperado:

```text
MATCH
```

---

## Teste 9 — Java negativo

```cmd
java Main verify MinhaSenha124! <signature>
```

Esperado:

```text
NO_MATCH
```

---

# 19. Mecanismos suportados pelo Pico-HSM

Os mecanismos podem ser consultados com:

```cmd
pkcs11-tool -M
```

No dispositivo utilizado nesta PoC foram encontrados, entre outros:

```text
SHA-1
SHA224
SHA256
SHA384
SHA512

ECDSA
ECDSA-SHA256
ECDSA-SHA384
ECDSA-SHA512

RSA-X-509
RSA-PKCS

SHA256-RSA-PKCS
SHA384-RSA-PKCS
SHA512-RSA-PKCS

RSA-PKCS-PSS
SHA256-RSA-PKCS-PSS
SHA384-RSA-PKCS-PSS
SHA512-RSA-PKCS-PSS

RSA-PKCS-OAEP

RSA-PKCS-KEY-PAIR-GEN
```

Para esta PoC foi utilizado:

```text
SHA512-RSA-PKCS
```

que corresponde, no Java, a:

```text
SHA512withRSA
```

---

# 20. Drawbacks e limitações

## Raspberry Pi Pico RP2040 não é um HSM corporativo

O Raspberry Pi Pico RP2040 é um microcontrolador de propósito geral.

Embora o Pico-HSM permita implementar interfaces e comportamentos semelhantes aos encontrados em um HSM, ele não oferece necessariamente as mesmas propriedades físicas e certificações de segurança encontradas em equipamentos corporativos.

Entre as diferenças podem estar:

* resistência física contra ataques;
* proteção contra side-channel attacks;
* tamper detection;
* tamper response;
* armazenamento especializado de chaves;
* secure elements dedicados;
* certificações de segurança;
* FIPS 140;
* Common Criteria;
* mecanismos corporativos de HA e backup;
* controles avançados de auditoria;
* gerenciamento de múltiplas partições/domínios.

Portanto:

```text
Pico-HSM ≠ HSM corporativo
```

O objetivo é validar **integração e arquitetura**, não equivalência de segurança.

---

## Ausência de HMAC

Uma limitação importante encontrada durante esta PoC foi a ausência de mecanismos HMAC expostos pelo Pico-HSM utilizado.

Por exemplo, não foram encontrados:

```text
SHA256-HMAC
SHA512-HMAC
```

Embora existam:

```text
SHA256
SHA512
```

essas operações são hashes puros.

Existe uma diferença fundamental:

```text
SHA-512(data)
```

não utiliza segredo.

Enquanto:

```text
HMAC-SHA512(secretKey, data)
```

depende de uma chave secreta.

Isso significa que o Pico utilizado nesta PoC não permite reproduzir diretamente um desenho baseado em HMAC protegido pelo HSM.

---

## SHA-512 não é password hashing

SHA-512 é uma função de hash criptográfica de propósito geral.

Não deve ser utilizada isoladamente como mecanismo de armazenamento de senhas:

```text
SHA512(password)
```

é rápido demais para esse propósito.

Sistemas reais devem utilizar mecanismos específicos para password hashing, por exemplo:

```text
Argon2
scrypt
PBKDF2
```

com salt e parâmetros adequados de custo.

---

## A PoC utiliza assinatura, não criptografia de senha

Embora informalmente possamos falar em “proteger a senha usando o HSM”, o fluxo desta PoC tecnicamente é:

```text
password
   ↓
SHA-512
   ↓
RSA signature
```

e não:

```text
password
   ↓
RSA encryption
```

O valor produzido é uma **assinatura digital**, não um ciphertext e não um password hash convencional.

---

## A assinatura não permite recuperar a senha

A operação:

```text
SHA512withRSA
```

não foi criada para permitir recuperação do conteúdo original.

A validação é feita verificando:

```text
password candidate
       +
signature
       +
public key
       ↓
verify()
       ↓
MATCH / NO_MATCH
```

---

## Verificação não exige HSM

A operação de geração da assinatura necessita da chave privada:

```text
GENERATE
   ↓
Private Key
   ↓
HSM
```

Entretanto, a verificação necessita apenas da chave pública:

```text
VERIFY
   ↓
Public Key
```

Portanto, tecnicamente:

```text
generate → HSM obrigatório

verify → HSM não obrigatório
```

Na implementação atual, o certificado pode ser obtido através do `KeyStore` PKCS#11, mas arquiteturalmente ele também poderia ser armazenado fora do HSM.

---

## PIN via terminal

A aplicação solicita o User PIN do Pico-HSM através do console.

Isso é adequado para laboratório, mas não representa necessariamente como uma aplicação corporativa deve obter credenciais de HSM.

Em produção, podem existir:

* secret managers;
* protected authentication paths;
* autenticação baseada em certificados;
* HSM credentials;
* aplicações autenticadas por partição;
* políticas específicas do fabricante.

O PIN nunca deve ser armazenado diretamente no código-fonte.

---

## Senha como argumento de linha de comando

Para simplificar a PoC utilizamos:

```cmd
java Main generate MinhaSenha123!
```

Isso **não é recomendado para produção**.

Argumentos de linha de comando podem ficar visíveis em:

* histórico do terminal;
* logs;
* ferramentas de administração;
* listagem de processos;
* scripts.

Uma implementação real deve receber o segredo por um canal mais apropriado.

---

## RSA PKCS#1 v1.5

A PoC utiliza:

```text
SHA512-RSA-PKCS
```

ou:

```text
SHA512withRSA
```

que corresponde a assinatura RSA PKCS#1 v1.5.

O Pico-HSM também anunciou suporte a:

```text
RSA-PKCS-PSS
SHA512-RSA-PKCS-PSS
```

RSA-PSS é geralmente preferível para novos protocolos de assinatura quando há liberdade para escolher o esquema.

O uso de PKCS#1 v1.5 nesta PoC foi escolhido principalmente pela simplicidade e interoperabilidade com:

```text
Java
OpenSSL
OpenSC
PKCS#11
```

---

# 21. O que esta PoC comprova

A PoC demonstra com sucesso:

```text
Java
  │
  ▼
SunPKCS11
  │
  ▼
OpenSC
  │
  ▼
PKCS#11
  │
  ▼
Pico-HSM
  │
  ▼
Private RSA Key
```

E comprova que:

* Java consegue acessar o Pico-HSM;
* OpenSC reconhece o dispositivo como SmartCard-HSM;
* PKCS#11 funciona como camada de abstração;
* Java consegue autenticar no token;
* uma chave RSA pode ser gerada dentro do HSM;
* a chave privada pode ser marcada como não exportável;
* Java consegue referenciar essa chave através de `SunPKCS11`;
* operações `SHA512withRSA` podem utilizar a chave do HSM;
* uma assinatura pode ser validada externamente;
* uma entrada diferente falha na validação;
* certificado e chave privada podem ser associados através do mesmo PKCS#11 ID.

---

# 22. Relação com um HSM corporativo

A principal ideia arquitetural da PoC é evitar dependência direta do Pico-HSM.

A aplicação utiliza:

```text
JCA/JCE
   ↓
SunPKCS11
   ↓
PKCS#11 implementation
```

No laboratório:

```text
SunPKCS11
   ↓
OpenSC
   ↓
Pico-HSM
```

Em produção, conceitualmente:

```text
SunPKCS11
   ↓
Vendor PKCS#11 Library
   ↓
Corporate HSM
```

Por exemplo:

```text
Application
      │
      ▼
CryptoService
      │
      ▼
JCA / JCE
      │
      ▼
SunPKCS11
      │
      ├── Lab
      │    └── OpenSC → Pico-HSM
      │
      └── Production
           └── Vendor PKCS#11 → Corporate HSM
```

Esse desacoplamento é um dos principais resultados que esta prova de conceito pretende validar.

---

# 23. Conclusão

O Raspberry Pi Pico RP2040 executando Pico-HSM oferece um ambiente de baixo custo para experimentar conceitos encontrados em integrações reais com HSMs.

Esta PoC não pretende reproduzir as garantias de segurança de um HSM corporativo.

Seu objetivo é validar:

```text
Java
+
JCA/JCE
+
SunPKCS11
+
PKCS#11
+
hardware externo
+
chaves privadas não exportáveis
```

permitindo desenvolver e testar a arquitetura da aplicação antes da integração com um HSM corporativo real.

O resultado esperado é que a implementação específica do dispositivo possa ser substituída posteriormente por uma biblioteca PKCS#11 fornecida pelo fabricante do HSM corporativo, preservando o máximo possível da arquitetura e das abstrações utilizadas pela aplicação.
