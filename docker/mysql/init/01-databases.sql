-- StickyBeak logical databases (one per bounded context, see docs/DATABASE_ER.md)
CREATE DATABASE IF NOT EXISTS stickybeak_auth    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS stickybeak_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS stickybeak_cart    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS stickybeak_order   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS stickybeak_payment DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
