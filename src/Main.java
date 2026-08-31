import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Base64;

public class Main {

    private static final String PKCS11_CONFIG =
            "C:/pico-hsm/pico-hsm-poc/pkcs11.cfg";

    private static final String KEY_ALIAS =
            "MinhaChave";

    private static final String SIGNATURE_ALGORITHM =
            "SHA512withRSA";

    public static void main(String[] args) {

        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String command = args[0].toLowerCase();

        try {

            switch (command) {

                case "generate" -> generate(args);

                case "verify" -> verify(args);

                default -> {
                    System.err.println(
                            "Comando inválido: " + command
                    );

                    printUsage();
                    System.exit(1);
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "Erro: " + e.getMessage()
            );

            e.printStackTrace();

            System.exit(2);
        }
    }

    // ============================================================
    // GENERATE
    // ============================================================

    private static void generate(String[] args)
            throws Exception {

        if (args.length < 2 || args.length > 3) {

            System.err.println(
                    "Uso: generate <senha> [arquivoAssinatura]"
            );

            System.exit(1);
        }

        char[] password =
                args[1].toCharArray();

        char[] pin = null;

        try {

            Provider pkcs11Provider =
                    loadPkcs11Provider();

            pin = readPin();

            KeyStore keyStore =
                    openKeyStore(
                            pkcs11Provider,
                            pin
                    );

            PrivateKey privateKey =
                    getPrivateKey(keyStore);

            byte[] passwordBytes =
                    new String(password)
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            Signature signer =
                    Signature.getInstance(
                            SIGNATURE_ALGORITHM,
                            pkcs11Provider
                    );

            signer.initSign(privateKey);

            signer.update(passwordBytes);

            byte[] signature =
                    signer.sign();

            String encoded =
                    Base64.getEncoder()
                            .encodeToString(signature);

            System.out.println(
                    "SIGNATURE_BASE64=" + encoded
            );

            if (args.length == 3) {

                Files.writeString(
                        Path.of(args[2]),
                        encoded,
                        StandardCharsets.US_ASCII
                );
            }

            Arrays.fill(
                    passwordBytes,
                    (byte) 0
            );

        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );

            if (pin != null) {

                Arrays.fill(
                        pin,
                        '\0'
                );
            }
        }
    }

    // ============================================================
    // VERIFY
    // ============================================================

    private static void verify(String[] args)
            throws Exception {

        if (args.length != 3) {

            System.err.println(
                    "Uso: verify <senha> <assinaturaBase64>"
            );

            System.exit(1);
        }

        char[] password =
                args[1].toCharArray();

        String signatureBase64 =
                args[2];

        char[] pin = null;

        try {

            Provider pkcs11Provider =
                    loadPkcs11Provider();

            /*
             * Para obter o certificado pelo KeyStore PKCS#11,
             * fazemos login no token.
             *
             * Tecnicamente a verificação em si não necessita
             * da chave privada.
             */
            pin = readPin();

            KeyStore keyStore =
                    openKeyStore(
                            pkcs11Provider,
                            pin
                    );

            PublicKey publicKey =
                    getPublicKey(keyStore);

            byte[] passwordBytes =
                    new String(password)
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );

            byte[] signature =
                    Base64.getDecoder()
                            .decode(signatureBase64);

            Signature verifier =
                    Signature.getInstance(
                            SIGNATURE_ALGORITHM
                    );

            verifier.initVerify(publicKey);

            verifier.update(passwordBytes);

            boolean matches =
                    verifier.verify(signature);

            if (matches) {

                System.out.println("MATCH");

                System.exit(0);

            } else {

                System.out.println("NO_MATCH");

                System.exit(3);
            }

        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );

            if (pin != null) {

                Arrays.fill(
                        pin,
                        '\0'
                );
            }
        }
    }

    // ============================================================
    // PKCS#11
    // ============================================================

    private static Provider loadPkcs11Provider()
            throws Exception {

        Provider base =
                Security.getProvider(
                        "SunPKCS11"
                );

        if (base == null) {

            throw new IllegalStateException(
                    "SunPKCS11 não encontrado."
            );
        }

        Provider provider =
                base.configure(
                        PKCS11_CONFIG
                );

        /*
         * Evita tentar registrar o mesmo provider
         * mais de uma vez.
         */
        if (
                Security.getProvider(
                        provider.getName()
                ) == null
        ) {

            Security.addProvider(
                    provider
            );
        }

        return provider;
    }

    private static KeyStore openKeyStore(
            Provider provider,
            char[] pin
    ) throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance(
                        "PKCS11",
                        provider
                );

        keyStore.load(
                null,
                pin
        );

        return keyStore;
    }

    // ============================================================
    // KEYS
    // ============================================================

    private static PrivateKey getPrivateKey(
            KeyStore keyStore
    ) throws Exception {

        if (
                !keyStore.containsAlias(
                        KEY_ALIAS
                )
        ) {

            throw new IllegalStateException(
                    "Alias não encontrado: "
                            + KEY_ALIAS
            );
        }

        if (
                !keyStore.isKeyEntry(
                        KEY_ALIAS
                )
        ) {

            throw new IllegalStateException(
                    "O alias não representa "
                            + "uma chave privada: "
                            + KEY_ALIAS
            );
        }

        PrivateKey privateKey =
                (PrivateKey)
                        keyStore.getKey(
                                KEY_ALIAS,
                                null
                        );

        if (privateKey == null) {

            throw new IllegalStateException(
                    "Chave privada não encontrada."
            );
        }

        return privateKey;
    }

    private static PublicKey getPublicKey(
            KeyStore keyStore
    ) throws Exception {

        Certificate certificate =
                keyStore.getCertificate(
                        KEY_ALIAS
                );

        if (certificate == null) {

            throw new IllegalStateException(
                    "Certificado não encontrado "
                            + "para o alias "
                            + KEY_ALIAS
            );
        }

        return certificate.getPublicKey();
    }

    // ============================================================
    // PIN
    // ============================================================

    private static char[] readPin() {

        Console console =
                System.console();

        if (console == null) {

            throw new IllegalStateException(
                    "Console não disponível. "
                            + "Execute pelo CMD ou PowerShell."
            );
        }

        return console.readPassword(
                "PIN do Pico-HSM: "
        );
    }

    // ============================================================
    // HELP
    // ============================================================

    private static void printUsage() {

        System.out.println("""
                Uso:

                  Gerar:
                    java Main generate <senha>

                  Verificar:
                    java Main verify <senha> <assinaturaBase64>

                Exemplos:

                  java Main generate MinhaSenha123!

                  java Main verify MinhaSenha123! AbCdEf...

                Exit codes:

                  0 = sucesso / MATCH
                  1 = argumentos inválidos
                  2 = erro da aplicação/HSM
                  3 = NO_MATCH
                """);
    }
}
