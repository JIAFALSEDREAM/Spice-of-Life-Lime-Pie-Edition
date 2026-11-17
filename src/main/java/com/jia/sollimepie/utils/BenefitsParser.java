package com.jia.sollimepie.utils;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.tracking.benefits.AttributeBenefit;
import com.jia.sollimepie.tracking.benefits.Benefit;
import com.jia.sollimepie.tracking.benefits.EffectBenefit;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BenefitsParser {
    public static List<List<Benefit>> parse(List<String> unparsed) {
        return parse(unparsed, warning -> SOLLimePie.LOGGER.warn(warning));
    }

    public static List<List<Benefit>> parse(List<String> unparsed, Consumer<String> warnings) {
        List<List<Benefit>> allBenefits = new ArrayList<>();

        int thresh = 0;
        for (String s : unparsed) {
            String[] thresholdBenefitsString = s.split(";", 0);
            List<Benefit> thresholdBenefits = new ArrayList<>();

            for (String benefitString : thresholdBenefitsString) {
                if (benefitString.isEmpty()) {
                    continue;
                }
                boolean is_detriment = false;
                switch(benefitString.charAt(0)){
                    case '-':
                        is_detriment = true;
                    case '+':
                        benefitString = benefitString.substring(1);
                    default:
                        break;
                }
                String[] benefitArgs = benefitString.split(",", 0);
                int len = benefitArgs.length;

                if (len < 2) {
                    warnings.accept("Invalid benefit specification: " + benefitString);
                    continue;
                }

                String benefitType;
                if ("attribute".equals(benefitArgs[0])) {
                    if (len < 3) {
                        warnings.accept("Need to specify a value when defining an attribute benefit: " + benefitString);
                        continue;
                    }
                    benefitType = "attribute";
                } else if ("effect".equals(benefitArgs[0])) {
                    benefitType = "effect";
                } else {
                    warnings.accept("Invalid benefit type: " + benefitArgs[0] + " in string " + benefitString);
                    continue;
                }

                String benefitName = benefitArgs[1];
                double benefitValue = 0;
                if (len > 2) {
                    try {
                        benefitValue = Double.parseDouble(benefitArgs[2]);
                    } catch (NumberFormatException e) {
                        warnings.accept("Invalid benefit value: " + benefitString);
                        continue;
                    }
                }

                if (!Double.isFinite(benefitValue)
                        || ("attribute".equals(benefitType) ? benefitValue == 0 : benefitValue < 0 || benefitValue > 255)) {
                    warnings.accept("Unsupported " + benefitType + " value: " + benefitString
                            + ("attribute".equals(benefitType) ? " (must be finite and nonzero)" : " (must be finite and between 0 and 255)"));
                    continue;
                }

                ResourceLocation benefitId;
                try {
                    benefitId = ResourceLocation.parse("attribute".equals(benefitType) && "generic.speed".equals(benefitName)
                            ? "generic.movement_speed" : benefitName);
                } catch (ResourceLocationException e) {
                    warnings.accept("Invalid " + benefitType + " ID in benefit: " + benefitString);
                    continue;
                }
                if (!("attribute".equals(benefitType) ? BuiltInRegistries.ATTRIBUTE.containsKey(benefitId)
                        : BuiltInRegistries.MOB_EFFECT.containsKey(benefitId))) {
                    warnings.accept("Unknown registered " + benefitType + " ID in benefit: " + benefitString);
                    continue;
                }

                if ("attribute".equals(benefitType)) {
                    thresholdBenefits.add(new AttributeBenefit(benefitName, benefitValue, thresh, is_detriment));
                }
                else {
                    thresholdBenefits.add(new EffectBenefit(benefitName, benefitValue, thresh, is_detriment));
                }
            }
            allBenefits.add(thresholdBenefits);
            thresh++;
        }

        return allBenefits;
    }
}
