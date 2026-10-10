package com.shop.backend.sales.batch;

import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

import java.time.LocalDate;

public class SalesPeriodParametersValidator implements JobParametersValidator {

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException{
        LocalDate from = requireLocalDate(parameters, "from");
        LocalDate to = requireLocalDate(parameters, "to");

        if(from.isAfter(to)){
            throw new InvalidJobParametersException("from(" + from + ")이 to("+ to +")보다 늦을 수 없습니다.");
        }
    }

    private LocalDate requireLocalDate(JobParameters parameters, String key) throws InvalidJobParametersException{
        JobParameter<?> parameter = parameters.getParameter(key);
        if(parameter == null || !(parameter.value() instanceof LocalDate date)){
            throw new InvalidJobParametersException(key + " 파라미터(LocalDate)가 필요합니다. 예: " + key +
                    "=2026-09-01,java.time.LocalDate");
        }
        return date;
    }
}
