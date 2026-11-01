-- Run this in the my_tg_my_gp PostgreSQL database.
-- Stores a short-lived bearer payment link and the server-authoritative amount/order/receipt.
CREATE TABLE IF NOT EXISTS public.house_tax_payment_sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    whatsapp_phone VARCHAR(20) NOT NULL,
    district_id INTEGER NOT NULL REFERENCES public.districts(district_id),
    mandal_id INTEGER NOT NULL REFERENCES public.mandals(mandal_id),
    gram_panchayat_id INTEGER NOT NULL REFERENCES public.gram_panchayats(gram_panchayat_id),
    assessment_no BIGINT NOT NULL REFERENCES public.house_tax_assessments(assessment_no),
    service_code VARCHAR(50) NOT NULL,
    search_method VARCHAR(30),
    search_value VARCHAR(150),
    amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED','ORDER_CREATED','PAID')),
    razorpay_order_id VARCHAR(100) UNIQUE,
    razorpay_payment_id VARCHAR(100) UNIQUE,
    receipt_number VARCHAR(50) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_house_tax_payment_sessions_assessment
    ON public.house_tax_payment_sessions(assessment_no);

CREATE INDEX IF NOT EXISTS idx_house_tax_payment_sessions_status
    ON public.house_tax_payment_sessions(status);

CREATE INDEX IF NOT EXISTS idx_house_tax_payment_sessions_expiry
    ON public.house_tax_payment_sessions(expires_at);
