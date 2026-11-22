package com.tourflow.planning;

import com.tourflow.common.web.ApiException;
import com.tourflow.planning.Dtos.RuleForm;
import com.tourflow.planning.model.RuleParam;
import com.tourflow.planning.repository.RuleParamRepository;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** РџР°СЂР°РјРµС‚СЂС‹ РїСЂР°РІРёР» VR-01вЂ¦VR-10 РЅР°СЃС‚СЂР°РёРІР°РµС‚ Р°РґРјРёРЅРёСЃС‚СЂР°С‚РѕСЂ Р±РµР· РїРµСЂРµРєРѕРјРїРёР»СЏС†РёРё (РўР— 7.1). */
@RestController
@RequestMapping("/api/v1/rules")
public class RulesController {
    private final RuleParamRepository rules;
    public RulesController(RuleParamRepository rules) { this.rules = rules; }

    @GetMapping @PreAuthorize("hasAnyRole('TOUR_MANAGER','ADMIN')")
    public List<RuleParam> list() { return rules.findAllByOrderByCodeAscParamNameAsc(); }

    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public RuleParam update(@PathVariable long id, @Valid @RequestBody RuleForm f) {
        RuleParam r = rules.findById(id).orElseThrow(() -> ApiException.notFound("РџСЂР°РІРёР»Рѕ"));
        if (f.value() < 0) throw ApiException.invalid("Р—РЅР°С‡РµРЅРёРµ РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РѕС‚СЂРёС†Р°С‚РµР»СЊРЅС‹Рј");
        r.setValue(f.value()); r.setActive(f.active());
        return r;
    }
}

