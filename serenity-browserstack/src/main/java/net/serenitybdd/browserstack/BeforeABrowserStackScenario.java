package net.serenitybdd.browserstack;

import net.serenitybdd.core.environment.EnvironmentSpecificConfiguration;
import net.serenitybdd.core.webdriver.driverproviders.CapabilityValue;
import net.serenitybdd.core.webdriver.enhancers.BeforeAWebdriverScenario;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.webdriver.SupportedWebDriver;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class BeforeABrowserStackScenario implements BeforeAWebdriverScenario {

    private static final String BROWSERSTACK = "browserstack.";
    private static final Map<String, String> LEGACY_TO_W3C = new HashMap<>();

    static {
        LEGACY_TO_W3C.put("os_version", "osVersion");
        LEGACY_TO_W3C.put("browser", "browserName");
        LEGACY_TO_W3C.put("browser_version", "browserVersion");
        LEGACY_TO_W3C.put("build", "buildName");
        LEGACY_TO_W3C.put("device", "deviceName");
        LEGACY_TO_W3C.put("appium_version", "appiumVersion");
    }

    // ["server", "user", "key"]
    private static List<String> NON_BSTACK_PROPERTIES
            = Arrays.asList(
            "browserName",
            "browserVersion",
            "server",
            "user",
            "key"
    );

    @Override
    public DesiredCapabilities apply(EnvironmentVariables environmentVariables,
                                     SupportedWebDriver driver,
                                     TestOutcome testOutcome,
                                     DesiredCapabilities capabilities) {

        if (driver != SupportedWebDriver.REMOTE) {
            return capabilities;
        }

        String remotePlatform = EnvironmentSpecificConfiguration.from(environmentVariables)
                .getOptionalProperty("remote.platform")
                .orElse(null);
        if (isNotEmpty(remotePlatform)) {
            capabilities.setPlatform(Platform.valueOf(remotePlatform));
        }

        Properties browserStackProperties = environmentVariables.getPropertiesWithPrefix(BROWSERSTACK);

        setNonW3CCapabilities(capabilities, browserStackProperties);

        List<String> w3cProperties = browserStackProperties.stringPropertyNames()
                .stream()
                .filter(this::isW3CProperty)
                .map(this::w3cKey)
                .collect(Collectors.toList());

        Map<String, Object> browserstackOptions = w3CPropertyMapFrom(browserStackProperties);
        String testName = testOutcome.getStoryTitle() + " - " + testOutcome.getTitle();
        browserstackOptions.put("sessionName", testName);

        capabilities.setCapability("bstack:options", browserstackOptions);
        return capabilities;
    }

    private void setNonW3CCapabilities(DesiredCapabilities capabilities, Properties browserStackProperties) {
        browserStackProperties.stringPropertyNames()
                .stream()
                .filter(this::isNonW3CProperty)
                .forEach(
                        key -> capabilities.setCapability(w3cKey(key), browserStackProperties.getProperty(key))
                );
    }

    private Map<String, Object> w3CPropertyMapFrom(Properties properties) {
        Map<String, Object> w3cOptions = new HashMap<>();
        Map<String,Map<String, Object>> nestedOptions = new HashMap<>();

        properties.stringPropertyNames()
                .stream()
                .filter(this::isW3CProperty)
                .forEach(
                        key -> {
                            String unprefixedKey = unprefixed(key);
                            String w3cKey = LEGACY_TO_W3C.getOrDefault(unprefixedKey, unprefixedKey);
                            if (w3cKey.contains(".")) {
                                String parentKey = StringUtils.split(w3cKey,".")[0];
                                String childKey = StringUtils.split(w3cKey,".")[1];
                                Map<String, Object> nestedProperties = nestedOptions.getOrDefault(parentKey, new HashMap<>());
                                nestedProperties.put(childKey, properties.getProperty(key));
                                nestedOptions.put(parentKey, nestedProperties);
                            } else {
                                w3cOptions.put(w3cKey, properties.getProperty(key));
                            }
                        }
                );
        w3cOptions.putAll(nestedOptions);
        return w3cOptions;
    }

    private boolean isNonW3CProperty(String key) {
        return !isW3CProperty(key);
    }

    private boolean isW3CProperty(String key) {
        return !NON_BSTACK_PROPERTIES.contains(w3cKey(unprefixed(key)));
    }

    private String w3cKey(String key) {
        return LEGACY_TO_W3C.getOrDefault(unprefixed(key), unprefixed(key));
    }

    private String unprefixed(String propertyName) {
        return propertyName.replace(BROWSERSTACK, "");
    }

}