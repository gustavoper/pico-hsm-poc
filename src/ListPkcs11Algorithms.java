import java.security.Provider;
import java.security.Security;

public class ListPkcs11Algorithms {

    private static final String CONFIG =
            "C:/pico-hsm/pico-hsm-poc/pkcs11.cfg";

    public static void main(String[] args) throws Exception {

        Provider base =
                Security.getProvider("SunPKCS11");

        Provider provider =
                base.configure(CONFIG);

        Security.addProvider(provider);

        System.out.println(
                "Provider: " + provider.getName()
        );

        System.out.println("\n=== CIPHERS ===");

        provider.getServices().stream()
                .filter(s ->
                        s.getType().equalsIgnoreCase("Cipher"))
                .map(Provider.Service::getAlgorithm)
                .sorted()
                .forEach(System.out::println);

        System.out.println("\n=== SIGNATURES ===");

        provider.getServices().stream()
                .filter(s ->
                        s.getType().equalsIgnoreCase("Signature"))
                .map(Provider.Service::getAlgorithm)
                .sorted()
                .forEach(System.out::println);
    }
}