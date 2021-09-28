package thito.fancywaystones.condition;

import org.bukkit.enchantments.*;
import org.bukkit.potion.*;
import thito.fancywaystones.*;
import thito.fancywaystones.condition.handler.*;
import thito.fancywaystones.config.*;

import java.util.*;
import java.util.function.*;

public class Condition {
    public static final Map<String, Function<MapSection, ConditionHandler>> HANDLER_FACTORY_MAP = new HashMap<>();
    public static final Map<String, Supplier<ConditionHandler>> NO_PARAM_HANDLER_FACTORY_MAP = new HashMap<>();

    static {
        HANDLER_FACTORY_MAP.put("RANDOM", map ->
                new RandomConditionHandler(map.getInteger("Chance").orElse(0)));
        HANDLER_FACTORY_MAP.put("ENCHANTED", map -> {
            Enchantment enchantment = map.getString("Type").map(Enchantment::getByName).orElse(null);
            if (enchantment != null) {
                return new EnchantedConditionHandler(enchantment, map.getInteger("Level").orElse(1));
            }
            return null;
        });
        HANDLER_FACTORY_MAP.put("EFFECT", map -> {
            PotionEffectType potionEffectType = map.getString("Type").map(PotionEffectType::getByName).orElse(null);
            if (potionEffectType != null) {
                return new EffectConditionHandler(potionEffectType, map.getInteger("Level").orElse(0));
            }
            return null;
        });
        HANDLER_FACTORY_MAP.put("LAND_ACCESS", map -> new LandAccessConditionHandler());
        HANDLER_FACTORY_MAP.put("IS_MEMBER", map -> new MemberConditionHandler());
        HANDLER_FACTORY_MAP.put("HAS_PERMISSION", map -> new PermissionConditionHandler(map.getString("Permission").orElse(null)));
        HANDLER_FACTORY_MAP.put("IS_OWNER", map -> new OwnerConditionHandler());

        NO_PARAM_HANDLER_FACTORY_MAP.put("ALWAYS", AlwaysConditionHandler::new);
        NO_PARAM_HANDLER_FACTORY_MAP.put("NEVER", NeverConditionHandler::new);
        NO_PARAM_HANDLER_FACTORY_MAP.put("LAND_ACCESS", LandAccessConditionHandler::new);
        NO_PARAM_HANDLER_FACTORY_MAP.put("RANDOM", () -> new RandomConditionHandler(50));
        NO_PARAM_HANDLER_FACTORY_MAP.put("IS_MEMBER", MemberConditionHandler::new);
        NO_PARAM_HANDLER_FACTORY_MAP.put("IS_OWNER", OwnerConditionHandler::new);
    }

    public static Condition fromConfig(ListSection listSection) {
        Condition condition = new Condition();
        if (listSection != null) {
            for (int i = 0; i < listSection.size(); i++) {
                listSection.getString(i).ifPresent(str -> {
                    Supplier<ConditionHandler> handler = NO_PARAM_HANDLER_FACTORY_MAP.get(str);
                    if (handler != null) {
                        condition.getRules().add(new ConditionRule(handler.get()));
                    }
                });
                listSection.getMap(i).ifPresent(mapSection -> {
                    for (String key : mapSection.getKeys()) {
                        Function<MapSection, ConditionHandler> factory = HANDLER_FACTORY_MAP.get(key);
                        if (factory != null) {
                            mapSection.getMap(key).ifPresent(map -> {
                                ConditionHandler handler = factory.apply(map);
                                if (handler != null) {
                                    ConditionRule rule = new ConditionRule(handler);
                                        map.getList("And").ifPresent(list -> {
                                        rule.setSubCondition(fromConfig(list));
                                    });
                                    condition.getRules().add(rule);
                                }
                            });
                        }
                    }
                });
            }
        }
        return condition;
    }

    private List<ConditionRule> rules = new ArrayList<>();

    public List<ConditionRule> getRules() {
        return rules;
    }

    public boolean test(Placeholder placeholder) {
        for (ConditionRule rule : rules) {
            if (rule.test(placeholder)) return true;
        }
        return rules.isEmpty();
    }
}