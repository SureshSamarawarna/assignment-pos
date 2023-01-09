package lk.ijse.dep9.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {
    private Integer orderId;
    private LocalDate orderDate;
    private String customerName;
    private Integer numOfItems;
    private BigDecimal total;
    private String customerId;
    private List<OrderDetailsDTO> orderDetails = new ArrayList<>();
}
