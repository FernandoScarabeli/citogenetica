package br.ufla.citogenetica.model.genericDto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserLocationDTO {
    @NotNull(message = "Latitude é obrigatória")
    private Double latitude;
    @NotNull(message = "Longitude é obrigatória")
    private Double longitude;
}
