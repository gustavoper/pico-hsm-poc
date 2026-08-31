import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;

import java.io.Console;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import sun.security.pkcs11.wrapper.CK_ATTRIBUTE;
import sun.security.pkcs11.wrapper.CK_MECHANISM;
import sun.security.pkcs11.wrapper.CK_RSA_PKCS_OAEP_PARAMS;
import sun.security.pkcs11.wrapper.PKCS11;
import sun.security.pkcs11.wrapper.PKCS11Exception;

import static sun.security.pkcs11.wrapper.PKCS11Constants.*;

public class MainPasswordCrypt {

    /*
     * DLL PKCS#11 do OpenSC.
     */
    private static final String PKCS11_LIBRARY =
            "C:/Program Files/OpenSC Project/OpenSC/pkcs11/opensc-pkcs11.dll";

    /*
     * Certificado correspondente à chave privada ID 01.
     *
     * Somente a chave pública será utilizada no comando encrypt.
     */
    private static final Path CERTIFICATE_FILE =
            Path.of(
                    "C:/pico-hsm/pico-hsm-poc/keys/cert01.pem"
            );

    /*
     * ID PKCS#11 da chave privada no Pico-HSM.
     *
     * pkcs11-tool mostra:
     *
     * ID: 1 (0x01)
     */
    private static final byte[] PRIVATE_KEY_ID = {
            0x01
    };

    /*
     * Derivação da senha.
     */
    private static final String KDF =
            "PBKDF2WithHmacSHA256";

    private static final int PBKDF2_ITERATIONS =
            210_000;

    private static final int VERIFIER_BITS =
            256;

    private static final int SALT_BYTES =
            16;

    /*
     * RSA-OAEP usado pelo lado Java para criptografia.
     *
     * Precisa corresponder exatamente aos parâmetros passados
     * posteriormente para CKM_RSA_PKCS_OAEP.
     */
    private static final OAEPParameterSpec JAVA_OAEP_PARAMS =
            new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );

    public static void main(String[] args) {

        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        try {

            switch (args[0].toLowerCase()) {

                case "encrypt" ->
                        encrypt(args);

                case "verify" ->
                        verify(args);

                default -> {
                    System.err.println(
                            "Comando inválido: " + args[0]
                    );

                    usage();
                    System.exit(1);
                }
            }

        } catch (Exception e) {

            System.err.println();
            System.err.println(
                    "Erro: " + e.getMessage()
            );

            e.printStackTrace();

            System.exit(2);
        }
    }

    // ============================================================
    // ENCRYPT
    // ============================================================

    private static void encrypt(String[] args)
            throws Exception {

        if (args.length != 2) {

            System.err.println(
                    "Uso: encrypt <senha>"
            );

            System.exit(1);
        }

        char[] password =
                args[1].toCharArray();

        byte[] verifier = null;

        try {

            /*
             * 1. Salt aleatório.
             */
            byte[] salt =
                    new byte[SALT_BYTES];

            SecureRandom.getInstanceStrong()
                    .nextBytes(salt);

            /*
             * 2. Password -> PBKDF2 verifier.
             */
            verifier =
                    deriveVerifier(
                            password,
                            salt
                    );

            /*
             * 3. Obtém apenas a chave pública
             *    a partir do certificado.
             */
            PublicKey publicKey =
                    loadPublicKey();

            /*
             * 4. Criptografa o verifier.
             *
             * Não há motivo para usar HSM aqui:
             * trata-se de uma operação com chave pública.
             */
            Cipher cipher =
                    Cipher.getInstance(
                            "RSA/ECB/OAEPPadding"
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    publicKey,
                    JAVA_OAEP_PARAMS
            );

            byte[] ciphertext =
                    cipher.doFinal(
                            verifier
                    );

            /*
             * 5. Valores a persistir.
             */
            System.out.println(
                    "ALGORITHM=PBKDF2-SHA256+RSA-OAEP-SHA256"
            );

            System.out.println(
                    "ITERATIONS="
                            + PBKDF2_ITERATIONS
            );

            System.out.println(
                    "SALT_BASE64="
                            + Base64.getEncoder()
                            .encodeToString(salt)
            );

            System.out.println(
                    "CIPHERTEXT_BASE64="
                            + Base64.getEncoder()
                            .encodeToString(ciphertext)
            );

            Arrays.fill(
                    ciphertext,
                    (byte) 0
            );

            Arrays.fill(
                    salt,
                    (byte) 0
            );

        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );

            if (verifier != null) {

                Arrays.fill(
                        verifier,
                        (byte) 0
                );
            }
        }
    }

    // ============================================================
    // VERIFY
    // ============================================================

    private static void verify(String[] args)
            throws Exception {

        if (args.length != 4) {

            System.err.println(
                    "Uso: verify <senha> <saltBase64> <ciphertextBase64>"
            );

            System.exit(1);
        }

        char[] password =
                args[1].toCharArray();

        byte[] salt =
                Base64.getDecoder()
                        .decode(args[2]);

        byte[] ciphertext =
                Base64.getDecoder()
                        .decode(args[3]);

        byte[] candidateVerifier = null;
        byte[] storedVerifier = null;

        char[] pin = null;

        try {

            /*
             * 1. Recria verifier da senha candidata.
             */
            candidateVerifier =
                    deriveVerifier(
                            password,
                            salt
                    );

            /*
             * 2. Solicita PIN do token.
             */
            pin = readPin();

            /*
             * 3. Decriptação diretamente via PKCS#11.
             *
             * Não utilizamos:
             *
             * Cipher.getInstance(..., SunPKCS11)
             *
             * porque o SunPKCS11-PicoHSM não expõe serviços
             * do tipo Cipher nesse setup.
             */
            storedVerifier =
                    decryptWithHsm(
                            ciphertext,
                            pin
                    );

            /*
             * 4. Comparação constant-time.
             */
            boolean matches =
                    MessageDigest.isEqual(
                            candidateVerifier,
                            storedVerifier
                    );

            if (matches) {

                System.out.println(
                        "MATCH"
                );

            } else {

                System.out.println(
                        "NO_MATCH"
                );

                System.exit(3);
            }

        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );

            Arrays.fill(
                    salt,
                    (byte) 0
            );

            Arrays.fill(
                    ciphertext,
                    (byte) 0
            );

            if (pin != null) {

                Arrays.fill(
                        pin,
                        '\0'
                );
            }

            if (candidateVerifier != null) {

                Arrays.fill(
                        candidateVerifier,
                        (byte) 0
                );
            }

            if (storedVerifier != null) {

                Arrays.fill(
                        storedVerifier,
                        (byte) 0
                );
            }
        }
    }

    // ============================================================
    // DIRECT PKCS#11
    // ============================================================

    private static byte[] decryptWithHsm(
            byte[] ciphertext,
            char[] pin
    ) throws Exception {

        /*
         * Carrega diretamente opensc-pkcs11.dll.
         *
         * Não estamos carregando SunPKCS11 Provider.
         */
        PKCS11 pkcs11 =
                PKCS11.getInstance(
                        PKCS11_LIBRARY,
                        "C_GetFunctionList",
                        null,
                        false
                );

        /*
         * Retorna apenas slots que possuem token.
         */
        long[] slots =
                pkcs11.C_GetSlotList(
                        true
                );

        if (slots.length == 0) {

            throw new IllegalStateException(
                    "Nenhum token PKCS#11 encontrado."
            );
        }

        /*
         * Na PoC temos somente o Pico-HSM.
         */
        long slot =
                slots[0];

        long session =
                0;

        boolean loggedIn =
                false;

        try {

            /*
             * Abre sessão serial read/write.
             */
            session =
                    pkcs11.C_OpenSession(
                            slot,
                            CKF_SERIAL_SESSION
                                    | CKF_RW_SESSION,
                            null,
                            null
                    );

            /*
             * Login como usuário normal.
             */
            pkcs11.C_Login(
                    session,
                    CKU_USER,
                    pin
            );

            loggedIn =
                    true;

            /*
             * Localiza:
             *
             * CKO_PRIVATE_KEY
             * CKA_ID = 01
             */
            long privateKeyHandle =
                    findPrivateKey(
                            pkcs11,
                            session
                    );

            /*
             * Configura parâmetros RSA-OAEP.
             *
             * Precisam corresponder ao Java:
             *
             * SHA-256
             * MGF1 SHA-256
             * empty label
             */
            CK_RSA_PKCS_OAEP_PARAMS oaep =
                    new CK_RSA_PKCS_OAEP_PARAMS();

            oaep.hashAlg =
                    CKM_SHA256;

            oaep.mgf =
                    CKG_MGF1_SHA256;

            oaep.source =
                    CKZ_DATA_SPECIFIED;

            /*
             * Empty OAEP label.
             */
            oaep.pSourceData =
                    new byte[0];

            CK_MECHANISM mechanism =
                    new CK_MECHANISM(
                            CKM_RSA_PKCS_OAEP
                    );

            /*
             * O CK_MECHANISM do wrapper não possui
             * construtor específico para OAEP.
             *
             * pParameter é público.
             */
            mechanism.pParameter =
                    oaep;

            /*
             * Equivalente PKCS#11:
             *
             * C_DecryptInit(
             *     session,
             *     CKM_RSA_PKCS_OAEP,
             *     privateKey
             * )
             */
            pkcs11.C_DecryptInit(
                    session,
                    mechanism,
                    privateKeyHandle
            );

            /*
             * RSA 2048 produz blocos de até 256 bytes.
             *
             * Utilizamos ciphertext.length como tamanho
             * máximo do buffer de saída.
             */
            byte[] output =
                    new byte[
                            ciphertext.length
                    ];

            int length =
                    pkcs11.C_Decrypt(
                            session,

                            0,
                            ciphertext,
                            0,
                            ciphertext.length,

                            0,
                            output,
                            0,
                            output.length
                    );

            /*
             * O verifier possui 32 bytes.
             *
             * C_Decrypt retorna o tamanho real.
             */
            return Arrays.copyOf(
                    output,
                    length
            );

        } finally {

            if (session != 0) {

                if (loggedIn) {

                    try {

                        pkcs11.C_Logout(
                                session
                        );

                    } catch (PKCS11Exception ignored) {
                    }
                }

                try {

                    pkcs11.C_CloseSession(
                            session
                    );

                } catch (PKCS11Exception ignored) {
                }
            }
        }
    }

    // ============================================================
    // FIND PRIVATE KEY
    // ============================================================

    private static long findPrivateKey(
            PKCS11 pkcs11,
            long session
    ) throws Exception {

        CK_ATTRIBUTE[] template = {

                new CK_ATTRIBUTE(
                        CKA_CLASS,
                        CKO_PRIVATE_KEY
                ),

                new CK_ATTRIBUTE(
                        CKA_ID,
                        PRIVATE_KEY_ID
                )
        };

        boolean initialized =
                false;

        try {

            pkcs11.C_FindObjectsInit(
                    session,
                    template
            );

            initialized =
                    true;

            long[] objects =
                    pkcs11.C_FindObjects(
                            session,
                            10
                    );

            if (objects.length == 0) {

                throw new IllegalStateException(
                        "Private Key ID 01 não encontrada no Pico-HSM."
                );
            }

            if (objects.length > 1) {

                System.err.println(
                        "Aviso: mais de uma chave privada "
                                + "corresponde ao ID 01. "
                                + "Usando a primeira."
                );
            }

            return objects[0];

        } finally {

            if (initialized) {

                pkcs11.C_FindObjectsFinal(
                        session
                );
            }
        }
    }

    // ============================================================
    // PBKDF2
    // ============================================================

    private static byte[] deriveVerifier(
            char[] password,
            byte[] salt
    ) throws Exception {

        PBEKeySpec spec =
                new PBEKeySpec(
                        password,
                        salt,
                        PBKDF2_ITERATIONS,
                        VERIFIER_BITS
                );

        try {

            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            KDF
                    );

            return factory
                    .generateSecret(
                            spec
                    )
                    .getEncoded();

        } finally {

            spec.clearPassword();
        }
    }

    // ============================================================
    // CERTIFICATE
    // ============================================================

    private static PublicKey loadPublicKey()
            throws Exception {

        CertificateFactory factory =
                CertificateFactory.getInstance(
                        "X.509"
                );

        try (
                FileInputStream input =
                        new FileInputStream(
                                CERTIFICATE_FILE.toFile()
                        )
        ) {

            X509Certificate certificate =
                    (X509Certificate)
                            factory.generateCertificate(
                                    input
                            );

            return certificate.getPublicKey();
        }
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

    private static void usage() {

        System.out.println("""
                Pico-HSM Password Crypt PoC
                ===========================

                Encrypt:

                  MainPasswordCrypt encrypt <senha>


                Verify:

                  MainPasswordCrypt verify \\
                    <senha> \\
                    <saltBase64> \\
                    <ciphertextBase64>


                Exit codes:

                  0 - operação concluída / MATCH
                  1 - parâmetros inválidos
                  2 - erro da aplicação ou HSM
                  3 - NO_MATCH
                """);
    }
}