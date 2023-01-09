package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.ijse.dep9.dto.OrderDTO;
import lk.ijse.dep9.dto.OrderDetailsDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@WebServlet(name = "order-servlet", urlPatterns = "/orders/*", loadOnStartup = 0)
public class OrderServlet extends HttpServlet {

    @Resource(lookup = "java:comp/env/jdbc/dep9-assignment")
    private DataSource pool;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (req.getPathInfo() != null && !req.getPathInfo().equals("/") ){
            resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }

        try {
            if (req.getContentType() == null || !req.getContentType().startsWith("application/json")){
                throw new JsonbException("Invalid JSON");
            }

            OrderDTO orderDTO = JsonbBuilder.create().fromJson(req.getReader(), OrderDTO.class);
            createNewOrder(orderDTO, resp);
        } catch (JsonbException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }

    }

    /*
        {
            "customerId": "C001",
            "orderDetails": [
                {
                    "code": "I001",
                    "qty": 2
                },
                {
                    "code": "I002",
                    "qty": 1
                }
            ]
        }
     */

    private void createNewOrder(OrderDTO orderDTO, HttpServletResponse resp) throws IOException {
        /*
         * 1. Validate the URL (/orders, /orders/) ✅
         * 2. Validate the content type of the request (application/json) ✅
         * 3. Convert the request's body (JSON) to a Java Object (DTO) via JSONB ✅
         * 4. Validate DTO (Data Validation)
         *   - Customer ID (can't be empty or null, should be matched "C\\d{3}" format) ✅
         *   - Order Details (can't be empty or null) ✅
         *   - Order Details can't have null value ✅
         *   - Order Details' code or qty can't be null values ✅
         *   - Order Details' code should be matched the "I\\d{3}" format ✅
         *   - Order Details' qty can't be negative value or zero ✅
         * 5. Business Validation
         *   - Check the existence of the Customer ID in the DB ✅
         *   - Check each item's existence of the order details in the DB and the availability ✅
         * 6. Within a transaction context ✅
         *   - Add a new Order ✅
         *   - Add each order detail ✅
         *   - Update the stock ✅
         * 7. Return the response
         *   - Status code: 201 ✅
         *   - { ✅
         *       "orderId": 1,
         *       "orderDate": "2022-10-15",
         *       "customerId": "C001",
         *       "customerName": "Tharindu",
         *       "numOfItems": 5,
         *       "total": 500.00
         *     }
         * */

        if (orderDTO.getCustomerId() == null || !orderDTO.getCustomerId().matches("C\\d{3}")){
            throw new JsonbException("Customer ID is empty or invalid");
        }else if (orderDTO.getOrderDetails() == null || orderDTO.getOrderDetails().isEmpty()){
            throw new JsonbException("Order Details are empty or null");
        }else if (orderDTO.getOrderDetails().stream().anyMatch(dto -> Objects.isNull(dto) ||
                Objects.isNull(dto.getCode()) || Objects.isNull(dto.getQty()))){
            throw new JsonbException("Null values are not allowed");
        }else if(!orderDTO.getOrderDetails().stream().allMatch(dto -> dto.getCode().matches("I\\d{3}") &&
                dto.getQty() > 0)){
            throw new JsonbException("Either an item code or qty is invalid");
        }

        try (Connection connection = pool.getConnection()) {

            PreparedStatement stm1 = connection.prepareStatement("SELECT id, name FROM Customer WHERE id=?");
            stm1.setString(1, orderDTO.getCustomerId());
            ResultSet resultSet = stm1.executeQuery();
            if (!resultSet.next()) throw new JsonbException("Customer doesn't exist in the database");

            final String customerName = resultSet.getString("name");
            final Map<String, BigDecimal> priceMap = new HashMap<>();
            final Map<String, Integer> qtyMap = new HashMap<>();

            PreparedStatement stm2 = connection.prepareStatement("SELECT qty, unit_price FROM Item WHERE code=?");
            for (OrderDetailsDTO orderDetail : orderDTO.getOrderDetails()) {
                stm2.setString(1, orderDetail.getCode());
                ResultSet rst = stm2.executeQuery();
                if (!rst.next()) throw new JsonbException(String.format("Item {%s} doesn't exist in the database", orderDetail.getCode()));
                if (rst.getInt("qty") < orderDetail.getQty()) throw new JsonbException(String.format("Not enough qty available for the item {%s}", orderDetail.getCode()));
                priceMap.put(orderDetail.getCode(), rst.getBigDecimal("unit_price"));
                qtyMap.put(orderDetail.getCode(), rst.getInt("qty"));
            }

            try {
                connection.setAutoCommit(false);

                PreparedStatement stmInsertOrder = connection.prepareStatement("INSERT INTO `Order` (date, customer_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
                PreparedStatement stmInsertOrderDetail = connection.prepareStatement("INSERT INTO OrderDetail (order_id, item_code, qty, unit_price) VALUES (?, ?, ?, ?)");
                PreparedStatement stmUpdateItemStock = connection.prepareStatement("UPDATE Item SET qty = ? WHERE code = ?");

                stmInsertOrder.setDate(1, Date.valueOf(LocalDate.now()));
                stmInsertOrder.setString(2, orderDTO.getCustomerId());
                if (stmInsertOrder.executeUpdate() != 1) throw new SQLException("Failed insert the order");

                ResultSet generatedKeys = stmInsertOrder.getGeneratedKeys();
                generatedKeys.next();
                int orderId = generatedKeys.getInt(1);

                BigDecimal total = BigDecimal.ZERO;
                for (OrderDetailsDTO orderDetail : orderDTO.getOrderDetails()) {
                    stmInsertOrderDetail.setInt(1, orderId);
                    stmInsertOrderDetail.setString(2, orderDetail.getCode());
                    stmInsertOrderDetail.setInt(3, orderDetail.getQty());
                    stmInsertOrderDetail.setBigDecimal(4, priceMap.get(orderDetail.getCode()));

                    if (stmInsertOrderDetail.executeUpdate() != 1) throw new SQLException("Failed to insert an order detail");

                    stmUpdateItemStock.setInt(1, qtyMap.get(orderDetail.getCode()) - orderDetail.getQty());
                    stmUpdateItemStock.setString(2, orderDetail.getCode());
                    if (stmUpdateItemStock.executeUpdate() != 1) throw new SQLException("Failed to update the stock");

                    total = total.add(priceMap.get(orderDetail.getCode()).multiply(new BigDecimal(orderDetail.getQty())));
                }

                connection.commit();

                resp.setStatus(HttpServletResponse.SC_CREATED);
                orderDTO.setOrderDetails(null);
                orderDTO.setOrderId(orderId);
                orderDTO.setOrderDate(LocalDate.now());
                orderDTO.setCustomerName(customerName);
                orderDTO.setTotal(total);
                resp.setContentType("application/json");
                JsonbBuilder.create().toJson(orderDTO, resp.getWriter());
            }catch (Throwable t){
                connection.rollback();
                t.printStackTrace();
                throw new SQLException(t);
            }finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to place the order");
        }
    }

}
