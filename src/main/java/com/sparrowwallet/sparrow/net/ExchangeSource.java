package com.sparrowwallet.sparrow.net;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.ExchangeRatesUpdatedEvent;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public enum ExchangeSource {
    NONE("None") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return Collections.emptyList();
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            return null;
        }
    },
    COINBASE("Coinbase") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return getRates().data.rates.keySet().stream().filter(code -> isValidISO4217Code(code.toUpperCase(Locale.ROOT)))
                    .map(code -> Currency.getInstance(code.toUpperCase(Locale.ROOT))).collect(Collectors.toList());
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            String currencyCode = currency.getCurrencyCode();
            OptionalDouble optRate = getRates().data.rates.entrySet().stream().filter(rate -> currencyCode.equalsIgnoreCase(rate.getKey())).mapToDouble(Map.Entry::getValue).findFirst();
            if(optRate.isPresent()) {
                return optRate.getAsDouble();
            }

            return null;
        }

        private CoinbaseRates getRates() {
            String url = "https://api.coinbase.com/v2/exchange-rates?currency=BTC";

            if(log.isInfoEnabled()) {
                log.info("Requesting exchange rates from " + url);
            }

            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                return httpClientService.requestJson(url, CoinbaseRates.class, null);
            } catch (Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving currency rates", e);
                } else {
                    log.warn("Error retrieving currency rates (" + e.getMessage() + ")");
                }
                return new CoinbaseRates();
            }
        }
    },
    COINGECKO("Coingecko") {
        @Override
        public List<Currency> getSupportedCurrencies() {
            return getRates().rates.entrySet().stream().filter(rate -> "fiat".equals(rate.getValue().type) && isValidISO4217Code(rate.getKey().toUpperCase(Locale.ROOT)))
                    .map(rate -> Currency.getInstance(rate.getKey().toUpperCase(Locale.ROOT))).collect(Collectors.toList());
        }

        @Override
        public Double getExchangeRate(Currency currency) {
            String currencyCode = currency.getCurrencyCode();
            OptionalDouble optRate = getRates().rates.entrySet().stream().filter(rate -> currencyCode.equalsIgnoreCase(rate.getKey())).mapToDouble(rate -> rate.getValue().value).findFirst();
            if(optRate.isPresent()) {
                return optRate.getAsDouble();
            }

            return null;
        }

        private CoinGeckoRates getRates() {
            String url = "https://api.coingecko.com/api/v3/exchange_rates";

            if(log.isInfoEnabled()) {
                log.info("Requesting exchange rates from " + url);
            }

            HttpClientService httpClientService = AppServices.getHttpClientService();
            try {
                return httpClientService.requestJson(url, CoinGeckoRates.class, null);
            } catch(Exception e) {
                if(log.isDebugEnabled()) {
                    log.warn("Error retrieving currency rates", e);
                } else {
                    log.warn("Error retrieving currency rates (" + e.getMessage() + ")");
                }
                return new CoinGeckoRates();
            }
        }
    };

    private static final Logger log = LoggerFactory.getLogger(ExchangeSource.class);

    private final String name;

    ExchangeSource(String name) {
        this.name = name;
    }

    public abstract List<Currency> getSupportedCurrencies();

    public abstract Double getExchangeRate(Currency currency);

    private static boolean isValidISO4217Code(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public static class CurrenciesService extends Service<List<Currency>> {
        private final ExchangeSource exchangeSource;

        public CurrenciesService(ExchangeSource exchangeSource) {
            this.exchangeSource = exchangeSource;
        }

        @Override
        protected Task<List<Currency>> createTask() {
            return new Task<>() {
                protected List<Currency> call() {
                    return exchangeSource.getSupportedCurrencies();
                }
            };
        }
    }

    public static class RatesService extends ScheduledService<ExchangeRatesUpdatedEvent> {
        private final ExchangeSource exchangeSource;
        private final Currency selectedCurrency;

        public RatesService(ExchangeSource exchangeSource, Currency selectedCurrency) {
            this.exchangeSource = exchangeSource;
            this.selectedCurrency = selectedCurrency;
        }

        protected Task<ExchangeRatesUpdatedEvent> createTask() {
            return new Task<>() {
                protected ExchangeRatesUpdatedEvent call() {
                    Double rate = exchangeSource.getExchangeRate(selectedCurrency);
                    return new ExchangeRatesUpdatedEvent(selectedCurrency, rate);
                }
            };
        }

        public ExchangeSource getExchangeSource() {
            return exchangeSource;
        }
    }

    private static class CoinbaseRates {
        public CoinbaseData data = new CoinbaseData();
    }

    private static class CoinbaseData {
        public String currency;
        public Map<String, Double> rates = new LinkedHashMap<>();
    }

    private static class CoinGeckoRates {
        public Map<String, CoinGeckoRate> rates = new LinkedHashMap<>();
    }

    private static class CoinGeckoRate {
        public String name;
        public String unit;
        public Double value;
        public String type;
    }
}
