"%JAVA_HOME%\keytool.exe" -genkeypair -alias poc-java -keyalg RSA  -keysize 2048 -sigalg SHA512withRSA ^
  -dname "CN=Pico HSM Java PoC" ^
  -validity 3650 ^
  -storetype PKCS11 ^
  -keystore NONE ^
  -addprovider SunPKCS11 ^
  -providerClass sun.security.pkcs11.SunPKCS11 ^
  -providerArg pkcs11.cfg




  openssl x509 -new ^
    -force_pubkey C:\pico-hsm\chavepublicadohsm.pem ^
    -CA poc-ca.pem ^
    -CAkey poc-ca-key.pem ^
    -set_serial 1 ^
    -days 3650 ^
    -subj "/CN=Pico HSM Java PoC" ^
    -sha512 ^
    -out cert01.pem