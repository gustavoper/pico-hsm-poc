# Pico-HSM Password Crypt — Java PoC

Proof of Concept de integração entre uma aplicação Java e um HSM executado em um **Raspberry Pi Pico RP2040**, utilizando **Pico-HSM, OpenSC e PKCS#11**.

O objetivo é validar uma arquitetura de **password matching dependente de uma chave privada não exportável armazenada no HSM**, simulando, em ambiente de laboratório, conceitos que posteriormente podem ser aplicados à integração com um HSM corporativo.

> **Importante:** esta aplicação é uma prova de conceito. Raspberry Pi Pico RP2040 + Pico-HSM não substituem um HSM corporativo e não oferecem necessariamente as mesmas garantias de segurança física, certificações, disponibilidade ou gerenciamento de chaves.

---

# 1. O que faz a aplicação

A classe:

```text
MainPasswordCrypt.java
```

implementa dois fluxos:

```text
encrypt
verify
```

O objetivo não é armazenar ou criptografar diretamente a senha.

A senha é primeiro submetida a uma função de derivação de chave (**KDF — Key Derivation Function**), produzindo um `verifier`.

Esse verifier é então protegido utilizando criptografia assimétrica RSA-OAEP.

A arquitetura é:

```text
Password
   │
   ▼
PBKDF2WithHmacSHA256
   │
   │ + random salt
   ▼
256-bit verifier
   │
   ▼
RSA-OAEP
   │
   ▼
Encrypted verifier
```

O valor persistido é, portanto, um **verifier criptografado**, e não a senha original.

---

# 2. Objetivo da PoC

A prova de conceito procura validar o seguinte fluxo:

```text
Java Application
       │
       ├── JCA/JCE
       │
       └── PKCS#11
              │
              ▼
       opensc-pkcs11.dll
              │
              ▼
           OpenSC
              │
              ▼
          Pico-HSM
              │
              ▼
    Raspberry Pi Pico RP2040
```

O principal requisito é:

> O processo de password matching deve depender de uma operação realizada com uma chave privada não exportável armazenada no HSM.

A aplicação não possui os bytes da chave privada.

---

# 3. Arquitetura criptográfica

## 3.1 Cadastro / Encrypt

O comando:

```cmd
MainPasswordCrypt encrypt <password>
```

executa:

```text
password
   │
   ▼
PBKDF2WithHmacSHA256
   │
   │ salt aleatório
   │ 210.000 iterations
   ▼
256-bit verifier
   │
   ▼
RSA-OAEP / SHA-256
   │
   │ Public Key
   ▼
ciphertext
```

A saída contém:

```text
ALGORITHM
ITERATIONS
SALT_BASE64
CIPHERTEXT_BASE64
```

Por exemplo:

```text
ALGORITHM=PBKDF2-SHA256+RSA-OAEP-SHA256
ITERATIONS=210000
SALT_BASE64=zVHX5ENddpNX5GkRthCyiA==
CIPHERTEXT_BASE64=Gy0qi/oz7nqDdF3g...
```

Esses são os valores que poderiam ser persistidos em banco de dados.

---

## 3.2 Password Match / Verify

O comando:

```cmd
MainPasswordCrypt verify <password> <salt> <ciphertext>
```

executa dois fluxos.

Primeiro:

```text
candidate password
        │
        ▼
PBKDF2WithHmacSHA256
        │
        │ stored salt
        ▼
candidateVerifier
```

Em paralelo, o valor armazenado é decriptado:

```text
stored ciphertext
        │
        ▼
PKCS#11
        │
        ▼
C_DecryptInit
        │
        ▼
CKM_RSA_PKCS_OAEP
        │
        ▼
C_Decrypt
        │
        ▼
Pico-HSM
        │
        ▼
Private Key ID 01
        │
        ▼
storedVerifier
```

Finalmente:

```text
candidateVerifier
        │
        │ constant-time comparison
        ▼
storedVerifier
        │
        ├── equal     → MATCH
        │
        └── different → NO_MATCH
```

A comparação é realizada utilizando:

```java
MessageDigest.isEqual(
    candidateVerifier,
    storedVerifier
);
```

---

# 4. Por que o HSM é necessário no Match

Esse é um dos principais pontos da PoC.

No cadastro utilizamos somente a chave pública:

```text
verifier
   ↓
RSA-OAEP
   ↓
Public Key
   ↓
ciphertext
```

Portanto, uma operação privada no HSM não é necessária nessa etapa.

Na validação, entretanto:

```text
ciphertext
   ↓
RSA-OAEP decrypt
   ↓
PRIVATE KEY
   ↓
storedVerifier
```

A chave privada é necessária.

Essa chave reside no Pico-HSM e foi configurada como:

```text
sensitive
always sensitive
never extractable
local
```

Portanto:

```text
REGISTER
   ↓
Public Key
   ↓
HSM não é necessário para a operação RSA


MATCH
   ↓
Private Key
   ↓
HSM necessário
```

Sem acesso à chave privada, o verifier armazenado não pode ser recuperado pelo fluxo normal da aplicação.

---

# 5. Por que RSA-OAEP

A PoC utiliza:

```text
RSA-OAEP
SHA-256
MGF1-SHA256
```

em vez de criptografia RSA PKCS#1 v1.5.

RSA-OAEP é probabilístico.

Isso significa que criptografar duas vezes o mesmo conteúdo produz ciphertexts diferentes:

```text
encrypt(verifier) → ciphertext A

encrypt(verifier) → ciphertext B

A != B
```

Portanto, não é possível realizar:

```text
encrypt(candidate) == storedCiphertext
```

para verificar uma senha.

O processo de validação precisa recuperar o verifier utilizando a chave privada.

---

# 6. Hardware utilizado

A PoC foi validada com:

```text
Raspberry Pi Pico
RP2040
Pico-HSM
SmartCard-HSM 6.6
USB CCID
```

O dispositivo foi identificado como:

```text
Pol Henarejos Pico Key CCID Interface 0
```

OpenSC identificou:

```text
SmartCard-HSM version 6.6
```

O token PKCS#11:

```text
token label        : Pico-HSM
token manufacturer : Pol Henarejos
token model        : PKCS#15 emulated

token flags:
    login required
    rng
    token initialized
    PIN initialized

firmware version   : 6.6
```

---

# 7. O que você precisa para começar

## Hardware

* Raspberry Pi Pico RP2040
* Firmware Pico-HSM instalado
* conexão USB

## Software

A PoC foi validada utilizando:

```text
Windows
Java 25
OpenSC
OpenSSL
Pico-HSM / SmartCard-HSM 6.6
```

---

# 8. OpenSC

O OpenSC fornece as ferramentas utilizadas durante os testes:

```text
opensc-tool
pkcs11-tool
pkcs15-tool
sc-hsm-tool
```

e principalmente a biblioteca:

```text
opensc-pkcs11.dll
```

No Windows, uma instalação típica está em:

```text
C:\Program Files\OpenSC Project\OpenSC\
```

A DLL utilizada pela aplicação é:

```text
C:\Program Files\OpenSC Project\OpenSC\pkcs11\opensc-pkcs11.dll
```

---

# 9. Java

A PoC foi validada utilizando:

```text
Java 25.0.4.1 LTS
```

Verifique:

```cmd
"%JAVA_HOME%\java.exe" -version
```

Também é necessário o módulo:

```text
jdk.crypto.cryptoki
```

Verifique:

```cmd
"%JAVA_HOME%\java.exe" --list-modules | findstr cryptoki
```

Esperado:

```text
jdk.crypto.cryptoki
```

---

# 10. Verificando o Pico-HSM

Conecte o dispositivo.

Execute:

```cmd
opensc-tool -l
```

Esperado:

```text
# Detected readers (pcsc)

0  Yes  Pol Henarejos Pico Key CCID Interface 0
```

Depois:

```cmd
opensc-tool -n
```

Esperado:

```text
SmartCard-HSM version 6.6
```

---

# 11. Inicializando o token

> **CUIDADO:** inicializar o Pico-HSM pode apagar chaves e certificados existentes.

Inicialização:

```cmd
sc-hsm-tool --initialize ^
  --so-pin <SO-PIN> ^
  --pin <USER-PIN>
```

O `SO-PIN` é utilizado para funções administrativas.

O `USER-PIN` é utilizado para autenticar operações protegidas.

Verifique:

```cmd
pkcs11-tool -L
```

Esperado:

```text
token label        : Pico-HSM

token flags:
    login required
    rng
    token initialized
    PIN initialized
```

---

# 12. Testando o login

Execute:

```cmd
pkcs11-tool --login -O
```

Esperado:

```text
Logging in to "Pico-HSM".
Please enter User PIN:
```

---

# 13. Criando a chave RSA

A PoC utiliza:

```text
RSA 2048 bits
ID 01
label MinhaChave
```

Crie:

```cmd
pkcs11-tool --login ^
  --keypairgen ^
  --key-type rsa:2048 ^
  --id 01 ^
  --label "MinhaChave"
```

Liste:

```cmd
pkcs11-tool --login -O
```

Esperado:

```text
Private Key Object; RSA 2048 bits

label: MinhaChave
ID: 1 (0x01)

Usage:
    decrypt
    sign
    signRecover

Access:
    sensitive
    always sensitive
    never extractable
    local
```

Também deverá existir:

```text
Public Key Object; RSA 2048 bits

label: MinhaChave
ID: 1 (0x01)
```

---

# 14. Exportando a chave pública

A chave privada não deve ser exportável.

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

# 15. Certificado X.509

Durante a primeira versão da PoC, foi criado um certificado X.509 associado à chave.

A estrutura final do token ficou:

```text
ID 01
 │
 ├── Private Key
 │      label: MinhaChave
 │
 ├── Public Key
 │      label: MinhaChave
 │
 └── Certificate
        label: MinhaChave
```

Para `MainPasswordCrypt`, o certificado é utilizado para disponibilizar a chave pública durante o `encrypt`.

Exemplo:

```text
keys/cert01.pem
```

---

# 16. Criando uma CA para laboratório

Crie a chave da CA:

```cmd
openssl genpkey ^
  -algorithm RSA ^
  -pkeyopt rsa_keygen_bits:2048 ^
  -out poc-ca-key.pem
```

Crie o certificado:

```cmd
openssl req -new -x509 ^
  -key poc-ca-key.pem ^
  -sha256 ^
  -days 3650 ^
  -subj "/CN=Pico HSM PoC CA" ^
  -out poc-ca.pem
```

Essa CA é utilizada apenas para a PoC.

---

# 17. Criando o certificado da chave do HSM

Utilizando a chave pública:

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

Valide:

```cmd
openssl verify ^
  -CAfile poc-ca.pem ^
  cert01.pem
```

Esperado:

```text
cert01.pem: OK
```

---

# 18. Mecanismos PKCS#11

Execute:

```cmd
pkcs11-tool -M
```

No Pico-HSM utilizado na PoC foram identificados:

```text
SHA-1
SHA224
SHA256
SHA384
SHA512

ECDSA
ECDSA-SHA1
ECDSA-SHA224
ECDSA-SHA256
ECDSA-SHA384
ECDSA-SHA512

RSA-X-509
RSA-PKCS

SHA1-RSA-PKCS
SHA256-RSA-PKCS
SHA384-RSA-PKCS
SHA512-RSA-PKCS

RSA-PKCS-PSS

SHA1-RSA-PKCS-PSS
SHA256-RSA-PKCS-PSS
SHA384-RSA-PKCS-PSS
SHA512-RSA-PKCS-PSS

RSA-PKCS-OAEP

RSA-PKCS-KEY-PAIR-GEN
```

Para esta PoC, o mecanismo mais importante é:

```text
RSA-PKCS-OAEP
keySize={1024,4096}
hw, decrypt
```

---

# 19. Teste de RSA decrypt diretamente no Pico-HSM

Antes da integração Java, a capacidade de RSA decrypt foi validada diretamente através do PKCS#11.

Criptografe um conteúdo utilizando a chave pública:

```cmd
openssl pkeyutl ^
  -encrypt ^
  -pubin ^
  -inkey chavepublicadodevice.pem ^
  -pkeyopt rsa_padding_mode:pkcs1 ^
  -in teste-rsa.txt ^
  -out teste-rsa.enc
```

Depois:

```cmd
pkcs11-tool --login ^
  --decrypt ^
  --mechanism RSA-PKCS ^
  --id 01 ^
  --input-file teste-rsa.enc ^
  --output-file teste-rsa.dec
```

Isso valida:

```text
ciphertext
   ↓
OpenSC
   ↓
PKCS#11
   ↓
Pico-HSM
   ↓
Private Key
   ↓
plaintext
```

---

# 20. Descoberta importante: SunPKCS11

Inicialmente a PoC tentou utilizar:

```java
Cipher.getInstance(
    "RSA/ECB/OAEPPadding",
    pkcs11Provider
);
```

O resultado foi:

```text
java.security.NoSuchAlgorithmException:
No such algorithm: RSA/ECB/OAEPPadding
```

Também foi testado:

```java
RSA/ECB/PKCS1Padding
```

com o mesmo resultado.

Para investigar, foram listados os serviços disponibilizados pelo provider:

```text
Provider: SunPKCS11-PicoHSM

=== CIPHERS ===

=== SIGNATURES ===
MD2withRSA
MD5withRSA
NONEwithECDSA
NONEwithRSA
RSASSA-PSS
SHA1withECDSA
SHA1withRSA
SHA256withECDSA
SHA256withRSA
SHA256withRSASSA-PSS
SHA384withRSA
SHA384withRSASSA-PSS
SHA512withECDSA
SHA512withRSA
...
```

O resultado mostrou que:

```text
SunPKCS11-PicoHSM

Signature   → disponível
Cipher      → não disponível
```

Isso explica por que a primeira PoC baseada em:

```text
SHA512withRSA
```

funcionou, mas RSA decrypt através de `Cipher` não funcionou.

---

# 21. Solução adotada

Para `MainPasswordCrypt`, a operação privada não utiliza:

```text
JCE Cipher
    ↓
SunPKCS11
```

Em vez disso, utiliza diretamente o wrapper PKCS#11 disponível no JDK:

```text
MainPasswordCrypt
       ↓
sun.security.pkcs11.wrapper
       ↓
opensc-pkcs11.dll
       ↓
PKCS#11
       ↓
Pico-HSM
```

O fluxo executado é equivalente a:

```text
C_GetSlotList

C_OpenSession

C_Login

C_FindObjectsInit
C_FindObjects
C_FindObjectsFinal

C_DecryptInit

C_Decrypt

C_Logout

C_CloseSession
```

A chave privada é localizada utilizando:

```text
CKA_CLASS = CKO_PRIVATE_KEY
CKA_ID    = 01
```

---

# 22. RSA-OAEP diretamente via PKCS#11

O decrypt utiliza:

```text
CKM_RSA_PKCS_OAEP
```

com:

```text
hashAlg = CKM_SHA256

mgf = CKG_MGF1_SHA256

source = CKZ_DATA_SPECIFIED

label = empty
```

Isso corresponde ao lado Java:

```text
RSA-OAEP
SHA-256
MGF1-SHA256
empty label
```

---

# 23. Compilando

A aplicação utiliza:

```text
sun.security.pkcs11.wrapper
```

que pertence ao módulo:

```text
jdk.crypto.cryptoki
```

Por isso é necessário exportar explicitamente o pacote.

Compile:

```cmd
"%JAVA_HOME%\javac.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -d out ^
  src\MainPasswordCrypt.java
```

---

# 24. Executando Encrypt

Execute:

```cmd
"%JAVA_HOME%\java.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -cp out ^
  MainPasswordCrypt encrypt MinhaSenha123!
```

Resultado:

```text
ALGORITHM=PBKDF2-SHA256+RSA-OAEP-SHA256
ITERATIONS=210000
SALT_BASE64=...
CIPHERTEXT_BASE64=...
```

O `encrypt` não necessita do PIN do HSM.

Isso é esperado porque RSA encryption utiliza somente a chave pública.

---

# 25. Executando Verify

Execute:

```cmd
"%JAVA_HOME%\java.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -cp out ^
  MainPasswordCrypt verify ^
  MinhaSenha123! ^
  <SALT_BASE64> ^
  <CIPHERTEXT_BASE64>
```

O HSM solicitará:

```text
PIN do Pico-HSM:
```

A aplicação então executará:

```text
C_Login
   ↓
C_FindObjects
   ↓
Private Key 01
   ↓
C_DecryptInit
   ↓
CKM_RSA_PKCS_OAEP
   ↓
C_Decrypt
```

Resultado esperado:

```text
MATCH
```

---

# 26. Teste positivo validado

A PoC foi validada com:

```text
password:
MinhaSenha123!
```

utilizando o salt e ciphertext gerados pelo comando `encrypt`.

Resultado:

```text
PIN do Pico-HSM:

MATCH
```

Isso demonstra que:

```text
PBKDF2(candidatePassword)
           ==
HSM.decrypt(storedCiphertext)
```

quando a senha é correta.

---

# 27. Teste negativo validado

Utilizando:

```text
MinhaSenha1333!
```

com exatamente o mesmo:

```text
salt
ciphertext
```

o resultado obtido foi:

```text
PIN do Pico-HSM:

NO_MATCH
```

Isso valida o fluxo esperado:

```text
correct password
       ↓
     MATCH


wrong password
       ↓
   NO_MATCH
```

---

# 28. Dados que seriam persistidos

Uma implementação baseada nessa arquitetura poderia armazenar algo semelhante a:

```text
user_id
algorithm
iterations
salt
encrypted_verifier
key_id
```

Exemplo conceitual:

```text
user_id             = 123

algorithm           =
PBKDF2-SHA256+RSA-OAEP-SHA256

iterations          =
210000

salt                =
zVHX5ENddpNX5GkRthCyiA==

encrypted_verifier  =
Gy0qi/oz7nqDdF3g...

key_id              =
01
```

A senha original não é armazenada.

---

# 29. Drawbacks e limitações

## 29.1 Raspberry Pi Pico RP2040 não é um HSM corporativo

Essa é a limitação mais importante.

O Raspberry Pi Pico RP2040 é um microcontrolador de propósito geral.

Pico-HSM fornece uma excelente plataforma para experimentar:

```text
PKCS#11
key generation
non-extractable keys
RSA
ECDSA
sign
decrypt
PIN authentication
```

mas isso não transforma o RP2040 em um HSM corporativo certificado.

Um HSM real pode possuir:

* hardware criptográfico dedicado;
* secure key storage;
* proteção física;
* tamper detection;
* tamper response;
* proteção contra side-channel attacks;
* controles administrativos;
* auditoria;
* particionamento;
* backup seguro;
* HA;
* políticas de quorum;
* FIPS 140;
* Common Criteria;
* mecanismos específicos de gerenciamento de chaves.

Portanto:

```text
Pico-HSM
    ≠
HSM corporativo
```

O objetivo desta PoC é validar **arquitetura e integração**, não equivalência de segurança.

---

## 29.2 Ausência de HMAC no Pico-HSM

Durante os testes:

```cmd
pkcs11-tool -M
```

foram encontrados:

```text
SHA256
SHA384
SHA512
```

mas não:

```text
SHA256-HMAC
SHA512-HMAC
```

É importante distinguir:

```text
SHA512(data)
```

de:

```text
HMAC-SHA512(secretKey, data)
```

SHA-512 sozinho não possui uma chave secreta.

HMAC possui.

Por essa razão, não foi possível implementar diretamente no Pico-HSM um fluxo baseado em:

```text
HMAC(secret, password)
```

---

## 29.3 PBKDF2/HMAC é executado pela JVM

Para contornar a ausência de HMAC no dispositivo, esta PoC utiliza:

```text
PBKDF2WithHmacSHA256
```

no próprio Java.

Portanto:

```text
password
   ↓
PBKDF2-HMAC-SHA256
   ↓
Java/JVM
```

e não:

```text
password
   ↓
HSM
   ↓
PBKDF2/HMAC
```

O HSM protege o verifier somente na etapa RSA.

---

## 29.4 O verifier existe temporariamente na memória da JVM

Durante o `encrypt`:

```text
password
   ↓
PBKDF2
   ↓
verifier
```

o verifier existe temporariamente na memória da aplicação.

Durante o `verify`, também existem temporariamente:

```text
candidateVerifier
storedVerifier
```

na memória da JVM.

A aplicação tenta minimizar isso utilizando:

```java
Arrays.fill(...)
```

após as operações.

Entretanto, Java possui:

* garbage collector;
* cópias internas;
* objetos temporários;
* otimizações da JVM.

Portanto, `Arrays.fill()` reduz a exposição, mas não oferece garantia absoluta de eliminação de todas as cópias da memória.

---

## 29.5 O HSM não executa toda a validação

A arquitetura atual é:

```text
HSM
 ↓
decrypt verifier
 ↓
JVM
 ↓
MessageDigest.isEqual()
```

Portanto, o HSM realiza a operação criptográfica privada, mas a comparação final ocorre na aplicação.

Um HSM corporativo com mecanismos específicos poderia permitir arquiteturas nas quais uma quantidade maior da operação acontecesse dentro do próprio dispositivo.

---

## 29.6 SunPKCS11 não expôs Cipher

Durante os testes, o provider:

```text
SunPKCS11-PicoHSM
```

expôs várias operações:

```text
Signature
```

mas nenhuma:

```text
Cipher
```

Por isso:

```java
Cipher.getInstance(
    "RSA/ECB/OAEPPadding",
    pkcs11Provider
);
```

falhou.

O mesmo ocorreu com:

```text
RSA/ECB/PKCS1Padding
```

Essa é uma limitação encontrada especificamente na combinação utilizada nesta PoC:

```text
Java 25
+
SunPKCS11
+
OpenSC
+
Pico-HSM
```

---

## 29.7 Uso de API interna do JDK

Para contornar a limitação anterior utilizamos:

```text
sun.security.pkcs11.wrapper
```

Isso permite acesso direto a:

```text
C_Login
C_FindObjects
C_DecryptInit
C_Decrypt
```

Entretanto, `sun.*` é API interna do JDK.

Por isso precisamos:

```text
--add-exports
```

na compilação e execução.

Isso não é ideal para uma biblioteca de produção.

Uma implementação corporativa deveria preferir:

* SDK oficial do fabricante do HSM;
* biblioteca PKCS#11 Java suportada;
* provider oficial;
* API pública e estável.

---

## 29.8 Dependência do OpenSC

Na PoC:

```text
Java
 ↓
PKCS#11 wrapper
 ↓
opensc-pkcs11.dll
 ↓
OpenSC
 ↓
Pico-HSM
```

Portanto existe uma dependência específica do OpenSC.

Em um HSM corporativo isso provavelmente seria substituído por:

```text
Java
 ↓
PKCS#11
 ↓
vendor-pkcs11.dll
 ↓
Corporate HSM
```

---

## 29.9 Senha na linha de comando

A PoC utiliza:

```cmd
MainPasswordCrypt encrypt MinhaSenha123!
```

Isso foi escolhido apenas para facilitar os testes.

Não é recomendado em produção.

A senha pode aparecer em:

* histórico do shell;
* scripts;
* logs;
* ferramentas de monitoração;
* informações do processo.

Uma implementação real deveria receber a senha através de um canal apropriado.

---

## 29.10 PIN

A aplicação solicita:

```text
PIN do Pico-HSM:
```

através do console.

Isso é suficiente para laboratório.

Uma aplicação corporativa pode precisar de:

* secret manager;
* credenciais protegidas;
* protected authentication path;
* service account;
* autenticação baseada em certificado;
* partições do HSM;
* políticas do fabricante.

O PIN não deve ser armazenado no código-fonte.

---

## 29.11 PBKDF2

A PoC utiliza:

```text
PBKDF2WithHmacSHA256
210000 iterations
256-bit output
```

Isso é adequado para demonstrar o conceito.

Os parâmetros de password hashing não devem, entretanto, ser tratados como constantes universais.

Uma implementação real deve avaliar:

* hardware;
* latência aceitável;
* threat model;
* política corporativa;
* evolução futura dos parâmetros.

Também podem ser considerados mecanismos modernos de password hashing, como Argon2id, dependendo dos requisitos e bibliotecas disponíveis.

---

## 29.12 RSA 2048

A PoC utiliza:

```text
RSA 2048
```

porque é suficiente para demonstrar:

```text
RSA-OAEP
+
PKCS#11
+
private key no HSM
```

A escolha do algoritmo e tamanho da chave em produção deve seguir os padrões e políticas criptográficas da organização.

---

# 30. O que a PoC efetivamente comprova

Ao final dos testes, foram validados:

```text
Raspberry Pi Pico RP2040
        ↓
Pico-HSM
        ↓
SmartCard-HSM
        ↓
OpenSC
        ↓
PKCS#11
        ↓
Java
```

Especificamente:

```text
Device detection                         OK

Token initialization                     OK

User PIN                                 OK

RSA key generation                       OK

Private key non-extractable              OK

Public key export                        OK

X.509 certificate                        OK

SHA512withRSA signature                  OK

RSA decrypt via PKCS#11                  OK

RSA-OAEP                                 OK

Direct PKCS#11 access from Java          OK

C_Login                                  OK

C_FindObjects                            OK

C_DecryptInit                            OK

C_Decrypt                                OK

PBKDF2 password verifier                 OK

Correct password                         MATCH

Wrong password                           NO_MATCH
```

---

# 31. Resultado final da PoC

A arquitetura final validada é:

```text
                 REGISTRATION

                  password
                     │
                     ▼
             PBKDF2-HMAC-SHA256
                     │
                     │ random salt
                     ▼
              256-bit verifier
                     │
                     ▼
                RSA-OAEP
                     │
                     │ Public Key
                     ▼
                ciphertext
                     │
                     ▼
                  storage


                 AUTHENTICATION

             candidate password
                     │
                     ▼
             PBKDF2-HMAC-SHA256
                     │
                     ▼
            candidateVerifier


               stored ciphertext
                     │
                     ▼
                   Java
                     │
                     ▼
                  PKCS#11
                     │
                     ▼
             opensc-pkcs11.dll
                     │
                     ▼
                  OpenSC
                     │
                     ▼
                 Pico-HSM
                     │
                     ▼
             Private Key ID 01
                     │
                     ▼
             CKM_RSA_PKCS_OAEP
                     │
                     ▼
              storedVerifier


candidateVerifier ───────────── storedVerifier
                     │
                     ▼
           constant-time comparison
                     │
              ┌──────┴──────┐
              ▼             ▼
            MATCH        NO_MATCH
```

---

# 32. Conclusão

A PoC demonstra que um Raspberry Pi Pico RP2040 executando Pico-HSM pode ser utilizado como plataforma de laboratório para desenvolver e validar integrações Java baseadas em PKCS#11.

O principal resultado é a validação de um fluxo no qual:

> **O password match depende de uma operação criptográfica realizada com uma chave privada não exportável armazenada em hardware externo.**

A implementação final utiliza:

```text
PBKDF2WithHmacSHA256
+
random salt
+
RSA-OAEP SHA-256
+
PKCS#11
+
non-extractable RSA private key
+
constant-time comparison
```

A arquitetura também permite separar claramente a aplicação da implementação física utilizada no laboratório.

Hoje:

```text
MainPasswordCrypt
       ↓
PKCS#11
       ↓
OpenSC
       ↓
Pico-HSM
       ↓
RP2040
```

Em uma futura integração corporativa:

```text
MainPasswordCrypt
       ↓
Crypto/HSM abstraction
       ↓
PKCS#11 / Vendor SDK
       ↓
Corporate HSM
```

Portanto, o Pico-HSM não é utilizado como substituto de um HSM corporativo.

Ele é utilizado como uma plataforma acessível para validar:

* integração Java ↔ HSM;
* PKCS#11;
* gerenciamento de chaves;
* chaves privadas não exportáveis;
* RSA-OAEP;
* autenticação no token;
* password verifier;
* password matching dependente do HSM;
* desenho de uma abstração que posteriormente possa utilizar um HSM corporativo real.

**Status da PoC: validada com sucesso.**
