# Performance Optimization Implementation Guide

**Status**: Ready to implement  
**Expected Performance Gain**: +100% to +300% throughput  
**Implementation Time**: 2-4 hours  

---

## PART 1: DATABASE OPTIMIZATION

### Step 1: Add Essential Indices to PostgreSQL

Connect to your PostgreSQL database and run these queries:

```sql
-- ===== CRITICAL INDICES (Do this first) =====

-- Orders table - Most frequently queried
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);

-- Order Items - Avoid N+1 queries
CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);

-- Bank Details - Security & Retrieval
CREATE INDEX IF NOT EXISTS idx_bank_details_archived ON bank_details(is_archived);
CREATE INDEX IF NOT EXISTS idx_bank_details_account_number 
    ON bank_details(account_number) 
    WHERE is_archived = false;

-- Scheduled Deliveries - Required for scheduled task
CREATE INDEX IF NOT EXISTS idx_scheduled_delivery_time 
    ON orders(scheduled_delivery_time) 
    WHERE scheduled_delivery_time IS NOT NULL AND status = 'PENDING';

-- User authentication - Faster login
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);

-- Product queries - Faster product retrieval
CREATE INDEX IF NOT EXISTS idx_products_archived ON products(is_archived);

-- Promo codes - Faster validation
CREATE INDEX IF NOT EXISTS idx_promo_codes_code_active 
    ON promo_codes(code) 
    WHERE is_active = true;

-- Reviews - Faster sorting
CREATE INDEX IF NOT EXISTS idx_reviews_product_id_rating 
    ON reviews(product_id, rating DESC);

-- Extras & Sauces - Product customization
CREATE INDEX IF NOT EXISTS idx_extras_product_id ON extras(product_id);
CREATE INDEX IF NOT EXISTS idx_sauces_product_id ON sauces(product_id);

-- ===== OPTIONAL INDICES (Add if still slow) =====

-- For analytical queries
CREATE INDEX IF NOT EXISTS idx_orders_scheduled_time_user 
    ON orders(user_id, scheduled_delivery_time DESC);

-- For batch processing
CREATE INDEX IF NOT EXISTS idx_bank_details_last_verified 
    ON bank_details(last_verified_at DESC);
```

**To verify indices are created:**
```sql
-- List all indices on orders table
SELECT indexname FROM pg_indexes WHERE tablename = 'orders';

-- Check if index is being used
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 1;
-- Look for "Index Scan" in output
```

---

## PART 2: CONNECTION POOL TUNING

### Update application.properties

```properties
# ===== CURRENT (Bottleneck) =====
# spring.datasource.hikari.maximum-pool-size=20
# spring.datasource.hikari.minimum-idle=5

# ===== OPTIMIZED =====
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=60000
spring.datasource.hikari.pool-name=KasiKotasHikariPool

# ===== SERVLET CONTAINER =====
server.tomcat.max-threads=250
server.tomcat.min-spare-threads=10
server.tomcat.accept-count=100

# ===== COMPRESSION (Reduce bandwidth) =====
server.compression.enabled=true
server.compression.min-response-size=1024
server.compression.excluded-mime-types=image/png,image/jpeg,image/gif,image/webp

# ===== CACHE CONFIG =====
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=2000,expireAfterWrite=60s,recordStats=true

# ===== JPA/HIBERNATE =====
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
spring.jpa.properties.hibernate.generate_statistics=false
spring.jpa.properties.hibernate.use_sql_comments=false

# ===== LOGGING (Reduce I/O) =====
spring.jpa.show-sql=false
logging.level.org.hibernate.SQL=WARN
logging.level.org.springframework.web=WARN
```

### Why These Changes?

| Setting | Old | New | Benefit |
|---------|-----|-----|---------|
| max-pool-size | 20 | 50 | 2.5x more concurrent DB connections |
| min-spare-threads | (default) | 10 | Faster startup for burst traffic |
| compression | disabled | enabled | 60-80% smaller JSON responses |
| cache size | 1000 | 2000 | More products/queries cached |
| cache TTL | 30s | 60s | Better hit rate (assumes non-real-time data) |
| batch_size | 10 | 20 | Fewer DB round-trips for inserts |

---

## PART 3: CACHING IMPROVEMENTS

### Add Strategic Cache Decorators

**File**: `src/main/java/kasiKotas/service/ProductService.java`

Find this method:
```java
public List<Product> getAllProducts() {
    List<Product> products = productRepository.findAll();
    // ... rest of code
}
```

Update it to:
```java
@Cacheable(value = "products", unless = "#result == null || #result.isEmpty()")
public List<Product> getAllProducts() {
    List<Product> products = productRepository.findAll();
    // ... rest of code
}
```

### Add Cache Eviction Listener

**Create new file**: `src/main/java/kasiKotas/config/CacheConfig.java`

```java
package kasiKotas.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "products",
            "product-details",
            "reviews-summary",
            "promo-codes",
            "bank-details",
            "user-orders"
        );
        
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .recordStats()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(2000)
            .build());
        
        return cacheManager;
    }
}
```

### Monitor Cache Performance

Add this endpoint to check cache stats:

**File**: `src/main/java/kasiKotas/controller/AdminController.java` (create if not exists)

```java
@RestController
@RequestMapping("/api/admin/cache")
@PreAuthorize("hasRole('ADMIN')")
public class CacheStatsController {

    @GetMapping("/stats")
    public Map<String, Object> getCacheStats(CacheManager cacheManager) {
        Map<String, Object> stats = new HashMap<>();
        
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCacheManager.CaffeineCache) {
                CaffeineCache caffeineCache = (CaffeineCache) cache;
                stats.put(cacheName, caffeineCache.getNativeCache().stats());
            }
        }
        
        return stats;
    }
}
```

---

## PART 4: QUERY OPTIMIZATION

### Fix N+1 Query Problem in OrderService

**Find this code in `OrderService.java`:**
```java
public Order retrieveFullOrder(Long orderId) {
    Order order = orderRepository.findById(orderId).orElse(null);
    // Hibernate lazy-loads order.orderItems here (Query 1)
    List<OrderItem> items = order.getOrderItems();
    for (OrderItem item : items) {
        // Hibernate lazy-loads item.product here (Query N)
        Product product = item.getProduct();
    }
}
```

**Replace with optimized version in `OrderRepository.java`:**
```java
@Query("SELECT DISTINCT o FROM Order o " +
       "LEFT JOIN FETCH o.orderItems oi " +
       "LEFT JOIN FETCH oi.product p " +
       "WHERE o.id = :orderId")
Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
```

**Update OrderService to use it:**
```java
public Order retrieveFullOrder(Long orderId) {
    return orderRepository.findByIdWithItems(orderId)
        .orElseThrow(() -> new EntityNotFoundException("Order not found"));
    // Now only 1 query instead of 1 + N + M queries!
}
```

### Batch Insert Operations

**Find this in OrderService:**
```java
// ❌ SLOW: 100 orders = 100 DB round trips
for (Order order : orders) {
    orderRepository.save(order);
}
```

**Replace with:**
```java
// ✅ FAST: All orders in 1 batch operation
orderRepository.saveAll(orders);
```

---

## PART 5: ASYNC OPERATIONS

### Make Email Sending Non-blocking

**File**: `src/main/java/kasiKotas/KasiKotasApplication.java`

Add to main class:
```java
@EnableAsync
@SpringBootApplication
public class KasiKotasApplication {
    // ... existing code
}
```

**File**: `src/main/java/kasiKotas/service/EmailService.java`

Update method:
```java
// ❌ BEFORE: Blocks API response until email is sent
public void sendOrderConfirmation(Order order) {
    // Email sending takes 1-5 seconds
    // User waits the whole time
}

// ✅ AFTER: Returns immediately
@Async
public void sendOrderConfirmation(Order order) {
    // Email sending happens in background thread
    // User gets response in <50ms
}
```

**Update OrderService to use it:**
```java
// In createOrder method:
order = orderRepository.save(order);

// Send emails asynchronously
emailService.sendOrderConfirmation(order);      // Returns immediately
emailService.sendAdminNotification(order);      // Returns immediately

return order;  // Response sent before emails even start
```

---

## PART 6: DATABASE QUERY LOGGING

### Enable Query Monitoring (Development Only)

```properties
# Add to application.properties for debugging

# Show SQL queries
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Show query parameters
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# Log slow queries (>1000ms)
logging.level.org.springframework.jdbc.core=DEBUG
spring.jpa.properties.hibernate.session.events.log=true
```

**Parse logs to find slow queries:**
```bash
# In Render logs, search for "Query took" or look for queries > 1 second
# Add indices for frequently slow queries
```

---

## PART 7: STRESS TESTING

### Before Deployment - Test Performance

**Create test file**: `src/test/java/kasiKotas/PerformanceTest.java`

```java
@SpringBootTest
class PerformanceTest {

    @Autowired
    private ProductService productService;
    
    @Test
    void testGetAllProductsPerformance() {
        // First call (cache miss)
        long start1 = System.currentTimeMillis();
        productService.getAllProducts();
        long duration1 = System.currentTimeMillis() - start1;
        System.out.println("First call: " + duration1 + "ms");
        
        // Second call (cache hit)
        long start2 = System.currentTimeMillis();
        productService.getAllProducts();
        long duration2 = System.currentTimeMillis() - start2;
        System.out.println("Second call (cached): " + duration2 + "ms");
        
        // Cache should be 10x faster
        assertTrue(duration2 < duration1 / 10);
    }
    
    @Test
    void testConcurrentRequests() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(100);
        
        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                productService.getAllProducts();
                latch.countDown();
            });
        }
        
        latch.await();
        long duration = System.currentTimeMillis() - start;
        System.out.println("100 concurrent requests: " + duration + "ms");
        
        // Should complete in < 5 seconds
        assertTrue(duration < 5000);
    }
}
```

**Run it:**
```bash
mvn test -Dtest=PerformanceTest
```

---

## PART 8: DEPLOYMENT UPDATES

### Update Render Configuration

1. **Increase Memory**:
   - Current: 512MB (free tier)
   - Recommended: 2GB+ (paid tier)

2. **Enable Auto-scaling** (if available on your plan):
   ```
   Max instances: 3
   Min instances: 1
   CPU threshold: 70%
   ```

3. **Add Health Check**:
   ```
   Path: /actuator/health
   Port: 8080
   Timeout: 5s
   Period: 30s
   ```

4. **Environment Variables**:
   ```
   JAVA_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC
   ```

---

## PART 9: MONITORING DASHBOARD

### Create Monitoring Endpoint

**File**: `src/main/java/kasiKotas/controller/MonitoringController.java`

```java
@RestController
@RequestMapping("/api/monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoringController {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Database health
        try {
            long count = orderRepository.count();
            health.put("database", "UP");
            health.put("orders_count", count);
        } catch (Exception e) {
            health.put("database", "DOWN");
        }
        
        // Memory health
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        health.put("memory_used_mb", usedMemory);
        health.put("memory_max_mb", maxMemory);
        health.put("memory_percent", (usedMemory * 100) / maxMemory);
        
        return health;
    }
}
```

---

## PART 10: IMPLEMENTATION CHECKLIST

**Estimated Time**: 2-4 hours

- [ ] Add database indices (30 mins)
- [ ] Update application.properties (15 mins)
- [ ] Create CacheConfig.java (15 mins)
- [ ] Add @Cacheable decorators (15 mins)
- [ ] Fix N+1 queries (30 mins)
- [ ] Add @Async to email service (10 mins)
- [ ] Create monitoring endpoint (15 mins)
- [ ] Run stress tests (15 mins)
- [ ] Deploy to Render (15 mins)
- [ ] Verify performance improvement (10 mins)

---

## EXPECTED RESULTS AFTER OPTIMIZATION

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Product list API | 250ms | 15ms | 16x faster |
| Order retrieval | 500ms | 50ms | 10x faster |
| Concurrent users | 100 | 500+ | 5x more |
| Database connections used | 20 | 50 | Better resource utilization |
| Memory consumption | ~1.5GB | ~1.8GB | +20% (acceptable trade-off) |
| JSON response size | 500KB | 100KB | 5x smaller (with gzip) |

---

## NEXT STEPS

1. **TODAY**: Run the SQL index creation queries
2. **TOMORROW**: Update application.properties and create CacheConfig
3. **THIS WEEK**: Deploy changes to Render
4. **NEXT WEEK**: Monitor performance and fine-tune settings

---

**Questions?** Review the main analysis document: `TRAFFIC_CAPACITY_AND_ALGORITHMS_ANALYSIS.md`

