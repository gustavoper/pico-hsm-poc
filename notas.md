# pico-hsm-poc
A Proof-of-concept Java APP for testing PicoHSM features.


Compilar: 

"%JAVA_HOME%\javac.exe" -d out src\Main.java


"%JAVA_HOME%\java.exe" -cp out Main generate MinhaSenha123!

SIGNATURE_BASE64=iBEY4mdVweopCUoYJcitzH39woKT7W1r0AHvuyOyC+iaXzdPYc+0KYSgaD669DsFLvEJ7C88A9+YHX5wioYEu+Sq+5CZwwxtloQ79qG6uM0GngmujaX/tPZDFH6d0VBbTSDzue/pGsDWWghWyEYDWnpGA5e9lUjZ70jjlvy7NEks1xLRs6XsmfzNjeVpHk18LOVN2tWrxdjKdUk6LyhF3pdqbkoi/vUKlloWQBEV9gzswopIkYJsl2oPFCqot6Le06lLQjX+8DINAXQ5uZESWklOwr2rcZVlT9hdtcj1jYzZGHKQgKNfYH5/5Fs+qKK0NtposuW7b0QxC96k7p1ZtQ==


"%JAVA_HOME%\java.exe" -cp out Main verify MinhaSenha123! iBEY4mdVweopCUoYJcitzH39woKT7W1r0AHvuyOyC+iaXzdPYc+0KYSgaD669DsFLvEJ7C88A9+YHX5wioYEu+Sq+5CZwwxtloQ79qG6uM0GngmujaX/tPZDFH6d0VBbTSDzue/pGsDWWghWyEYDWnpGA5e9lUjZ70jjlvy7NEks1xLRs6XsmfzNjeVpHk18LOVN2tWrxdjKdUk6LyhF3pdqbkoi/vUKlloWQBEV9gzswopIkYJsl2oPFCqot6Le06lLQjX+8DINAXQ5uZESWklOwr2rcZVlT9hdtcj1jYzZGHKQgKNfYH5/5Fs+qKK0NtposuW7b0QxC96k7p1ZtQ== 

"%JAVA_HOME%\java.exe" -cp out Main verify MinhaSenha124! iBEY4mdVweopCUoYJcitzH39woKT7W1r0AHvuyOyC+iaXzdPYc+0KYSgaD669DsFLvEJ7C88A9+YHX5wioYEu+Sq+5CZwwxtloQ79qG6uM0GngmujaX/tPZDFH6d0VBbTSDzue/pGsDWWghWyEYDWnpGA5e9lUjZ70jjlvy7NEks1xLRs6XsmfzNjeVpHk18LOVN2tWrxdjKdUk6LyhF3pdqbkoi/vUKlloWQBEV9gzswopIkYJsl2oPFCqot6Le06lLQjX+8DINAXQ5uZESWklOwr2rcZVlT9hdtcj1jYzZGHKQgKNfYH5/5Fs+qKK0NtposuW7b0QxC96k7p1ZtQ==




"%JAVA_HOME%\javac.exe" -d out src\MainPasswordCrypt.java


c:\pico-hsm\pico-hsm-poc>"%JAVA_HOME%\java.exe" ^
 -cp out ^
 MainPasswordCrypt encrypt MinhaSenha123!

SALT_BASE64=8dIhBFLpSA6FSlaQ1AUhDQ==
CIPHERTEXT_BASE64=rwRVLvtHVsQAt8pEhTz3utVM5aFbhtaBiFKxDWAkhuWRmADRdv3IetGOTmYjY/+4aT6sDimFLQal11ZArTekAGAR7FU6jrpfR81pNQc/pLC49t99GvpMnzWHBkp0MhOSqNh8LAuTO8Xs2KB6HBwkUNxOLQ3S4lCKZtEIabSHRB7iXM5u7JWP8h3C4PR/0uSiml5GQMlhf0WvDaNI++aM1aShXzJUjh/qoYdWjwojTGY/PBl9XaCoi31bhrHC0SK78Q/UosaTMBWH5Bz/st5XI6UonyjDJgVb6nVFANyk6Q8YiA94PkYzpgOQ83q7q1REqwQWmNPmbk9KUa3FllVifQ==
ITERATIONS=210000


"%JAVA_HOME%\java.exe" -cp out  MainPasswordCrypt verify ^
  MinhaSenha123! ^
  8dIhBFLpSA6FSlaQ1AUhDQ== ^
  rwRVLvtHVsQAt8pEhTz3utVM5aFbhtaBiFKxDWAkhuWRmADRdv3IetGOTmYjY/+4aT6sDimFLQal11ZArTekAGAR7FU6jrpfR81pNQc/pLC49t99GvpMnzWHBkp0MhOSqNh8LAuTO8Xs2KB6HBwkUNxOLQ3S4lCKZtEIabSHRB7iXM5u7JWP8h3C4PR/0uSiml5GQMlhf0WvDaNI++aM1aShXzJUjh/qoYdWjwojTGY/PBl9XaCoi31bhrHC0SK78Q/UosaTMBWH5Bz/st5XI6UonyjDJgVb6nVFANyk6Q8YiA94PkYzpgOQ83q7q1REqwQWmNPmbk9KUa3FllVifQ==



BUG: Lista algos suportados pelo java 25

"%JAVA_HOME%\javac.exe" ^
  -d out ^
  src\ListPkcs11Algorithms.java

  "%JAVA_HOME%\java.exe" ^
  -cp out ^
  ListPkcs11Algorithms


  c:\pico-hsm\pico-hsm-poc>  "%JAVA_HOME%\java.exe" ^
Mais?   -cp out ^
Mais?   ListPkcs11Algorithms
Provider: SunPKCS11-PicoHSM

=== CIPHERS ===

=== SIGNATURES ===
MD2withRSA
MD5withRSA
NONEwithECDSA
NONEwithECDSAinP1363Format
NONEwithRSA
RSASSA-PSS
SHA1withECDSA
SHA1withECDSAinP1363Format
SHA1withRSA
SHA1withRSASSA-PSS
SHA224withECDSA
SHA224withECDSAinP1363Format
SHA224withRSA
SHA256withECDSA
SHA256withECDSAinP1363Format
SHA256withRSA
SHA256withRSASSA-PSS
SHA3-224withECDSA
SHA3-224withECDSAinP1363Format
SHA3-224withRSA
SHA3-256withECDSA
SHA3-256withECDSAinP1363Format
SHA3-256withRSA
SHA3-384withECDSA
SHA3-384withECDSAinP1363Format
SHA3-384withRSA
SHA3-512withECDSA
SHA3-512withECDSAinP1363Format
SHA3-512withRSA
SHA384withECDSA
SHA384withECDSAinP1363Format
SHA384withRSA
SHA384withRSASSA-PSS
SHA512withECDSA
SHA512withECDSAinP1363Format
SHA512withRSA

openssl pkeyutl ^
  -encrypt ^
  -pubin ^
  -inkey pico-hsm-poc\keys\chavepublicadodevice.pem ^
  -pkeyopt rsa_padding_mode:pkcs1 ^
  -in teste-rsa.txt ^
  -out teste-rsa.enc



  "%JAVA_HOME%\javac.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -d out ^
  src\MainPasswordCrypt.java





  "%JAVA_HOME%\java.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -cp out ^
  MainPasswordCrypt encrypt MinhaSenha123!

  SALT_BASE64=zVHX5ENddpNX5GkRthCyiA==
CIPHERTEXT_BASE64=Gy0qi/oz7nqDdF3gCSntQj5P4wQ2jXrF6H4E+Q/dXJfqRHvHjLpZDWeMRyeb00M8Ugm1oR5+p9vYxond5VbWJ9lE8C4dmnO0TdgF3fCgR4V44d0ppMleJBJ1cGXtd7nbtA+u1G+yr9rtcL8JaDI4w4zetJ/bGBjLM5wVUVtdDlhjIoa+DQseUsVe1SqUe0ebBOW+0h0QFszYwG8XITyCCjbLBDaSccNtKbhY3oU0D9QsY3ZW4h6+zHzNHTfZpSEE/Su2/m6AlOgh833oKKnVofQMpGpJBI7rCvVSKTOzN9m1jTrqyyVJviRqXXO7a+WPhQKl51IeNEwEYrmprcrMSA==


"%JAVA_HOME%\java.exe" ^
  --add-modules jdk.crypto.cryptoki ^
  --add-exports jdk.crypto.cryptoki/sun.security.pkcs11.wrapper=ALL-UNNAMED ^
  -cp out ^
  MainPasswordCrypt verify ^
  MinhaSenha1333! ^
  zVHX5ENddpNX5GkRthCyiA== ^
  Gy0qi/oz7nqDdF3gCSntQj5P4wQ2jXrF6H4E+Q/dXJfqRHvHjLpZDWeMRyeb00M8Ugm1oR5+p9vYxond5VbWJ9lE8C4dmnO0TdgF3fCgR4V44d0ppMleJBJ1cGXtd7nbtA+u1G+yr9rtcL8JaDI4w4zetJ/bGBjLM5wVUVtdDlhjIoa+DQseUsVe1SqUe0ebBOW+0h0QFszYwG8XITyCCjbLBDaSccNtKbhY3oU0D9QsY3ZW4h6+zHzNHTfZpSEE/Su2/m6AlOgh833oKKnVofQMpGpJBI7rCvVSKTOzN9m1jTrqyyVJviRqXXO7a+WPhQKl51IeNEwEYrmprcrMSA==