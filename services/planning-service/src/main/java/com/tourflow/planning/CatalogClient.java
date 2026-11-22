package com.tourflow.planning;

import com.tourflow.common.dto.Contracts.*;
import com.tourflow.common.web.ApiException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/** Перевод технических сбоев в понятные ошибки; повтор одного запроса при сетевом сбое (ТЗ 11, надёжность). */
@Component
public class CatalogClient {
    private final CatalogApi api;
    public CatalogClient(CatalogApi api) { this.api = api; }

    private <T> T call(Supplier<T> s) {
        RuntimeException last = null;
        for (int i = 0; i < 2; i++) {
            try { return s.get(); }
            catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) throw ApiException.notFound("Объект каталога");
                throw ApiException.invalid("Каталог отклонил запрос: " + e.getStatusCode());
            }
            catch (RuntimeException e) { last = e; try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }
        }
        throw ApiException.unavailable("Каталог временно недоступен, актуальность данных не проверена");
    }

    public CheckResult check(CheckItem item) { return call(() -> api.check(List.of(item))).get(0); }
    public List<CheckResult> check(List<CheckItem> items) { return call(() -> api.check(items)); }
    public List<CheckResult> candidates(CandidateRequest r) { return call(() -> api.candidates(r)); }
    public DestinationDto destination(long id) { return call(() -> api.destination(id)); }
    public List<DestinationDto> destinations() { return call(api::destinations); }
}
