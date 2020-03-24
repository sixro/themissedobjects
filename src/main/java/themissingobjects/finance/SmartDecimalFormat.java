package themissingobjects.finance;


import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a {@code DecimalFormat} smart enough to be able to parse currencies without failing as the one provided by Java.
 *
 * <p>
 * The short story is that if you execute the following code:
 * </p>
 * <code>
 *     BigDecimal value = new BigDecimal("1.23");
 *
 *     DecimalFormat df = new DecimalFormat("¤#,##0.00", new DecimalFormatSymbols(Locale.US));
 *     df.setCurrency(Currency.getInstance("EUR"));
 *     String text = df.format(value);
 *
 *     DecimalFormat df2 = new DecimalFormat("¤#,##0.00", new DecimalFormatSymbols(Locale.US));
 *     df2.setParseBigDecimal(true);
 *     df2.parse(text);
 * </code>
 * <p>
 * it will fail for a {@code ParseException}, but if you change the currency
 * to {@code USD} or {@code GBP} it will work.<br/>
 * Lots of people explain this behaviour saying that it is right because it depends on the locale.
 * If this is true (and I would like to know the answer of the developer that wrote that code), even the
 * format should fail. PERIOD.
 * </p>
 * <p>
 * {@code SmartDecimalFormat} is able to parse the following representations:
 * </p>
 * <code>
 * €1.23
 * 1.23€
 * EUR1.23
 * 1.23EUR
 * </code>
 */
public class SmartDecimalFormat extends DecimalFormat {

    public static final Pattern CURRENCY_PATTERN = Pattern.compile("([^0-9\\-\\.,]*)([0-9\\-\\.,]+)([^0-9\\-\\.,]*)");

    private final Locale locale;

    public SmartDecimalFormat(String pattern, Locale locale) {
        super(pattern, new DecimalFormatSymbols(locale));
        this.locale = locale;
    }

    public SmartDecimalFormat(String pattern, DecimalFormatSymbols symbols, Locale locale) {
        super(pattern, symbols);
        this.locale = locale;
    }

    @Override
    public Number parse(String source) throws ParseException {
        if (! toPattern().contains("¤"))
            return super.parse(source);

        Matcher matcher = CURRENCY_PATTERN.matcher(source);
        if (! matcher.matches())
            return super.parse(source);

        String group1 = matcher.group(1);
        String group2 = matcher.group(2);
        String group3 = matcher.group(3);

        String currencyAsText = group1.trim().length() > 0 ? group1.trim() : group3.trim();
        if (currencyAsText.isEmpty())
            return super.parse(source);

        String numberAsText = group2;

        // NOTE: changing the current pattern temporarely is a really bad practice, but
        // the aim is to not fail due to the not presence anymore of the currency symbol
        // (failures have been found for example on € symbol in Locale.US).
        // Because the DecimalFormat is already not thread safe I do not synchronize this code.
        // But I replace the originalPattern at the end.
        Number parsed = null;
        String originalPattern = toPattern();
        try {
            applyPattern(originalPattern.replace("¤", ""));
            parsed = super.parse(numberAsText);

            Currency currency = (currencyAsText.length() == 3)
                ? Currency.getInstance(currencyAsText)
                : currencyBySymbol(currencyAsText, locale);
            setCurrency(currency);

            return parsed;
        } finally {
            applyPattern(originalPattern);
        }
    }

    public static Currency currencyBySymbol(String currencySymbol, Locale locale) {
        Set<Currency> currencies = Currency.getAvailableCurrencies();
        Map<String, Currency> currencyBySymbol = new HashMap<>();
        for (Currency c: currencies) currencyBySymbol.put(c.getSymbol(locale), c);
        return currencyBySymbol.get(currencySymbol);
    }

}