CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY,
  name varchar(160) NOT NULL,
  email varchar(320) UNIQUE NOT NULL,
  password_hash text NOT NULL,
  role varchar(20) NOT NULL CHECK (role IN ('student','instructor','admin')),
  status varchar(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active','pending','suspended','disabled')),
  expertise varchar(180),
  email_verified boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sessions (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash char(64) UNIQUE NOT NULL,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS sessions_user_idx ON sessions(user_id);
CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS courses (
  id uuid PRIMARY KEY,
  instructor_id uuid REFERENCES users(id) ON DELETE SET NULL,
  title varchar(220) NOT NULL,
  slug varchar(240) UNIQUE NOT NULL,
  subtitle varchar(320),
  description text,
  education_level varchar(40) NOT NULL DEFAULT 'professional',
  category varchar(120),
  price_cents integer NOT NULL DEFAULT 0 CHECK (price_cents >= 0),
  currency char(3) NOT NULL DEFAULT 'USD',
  status varchar(30) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','under_review','changes_requested','published','suspended','archived')),
  quality_score smallint CHECK (quality_score BETWEEN 0 AND 100),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS courses_instructor_idx ON courses(instructor_id);
CREATE INDEX IF NOT EXISTS courses_status_idx ON courses(status);

CREATE TABLE IF NOT EXISTS enrolments (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  progress smallint NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
  enrolled_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  UNIQUE(student_id, course_id)
);

CREATE TABLE IF NOT EXISTS orders (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  status varchar(30) NOT NULL DEFAULT 'pending',
  payment_method varchar(60),
  currency char(3) NOT NULL DEFAULT 'USD',
  total_cents integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  paid_at timestamptz
);

CREATE TABLE IF NOT EXISTS order_items (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
  unit_price_cents integer NOT NULL CHECK (unit_price_cents >= 0)
);

CREATE TABLE IF NOT EXISTS conversations (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  instructor_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid REFERENCES courses(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(student_id, instructor_id, course_id)
);

CREATE TABLE IF NOT EXISTS messages (
  id uuid PRIMARY KEY,
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 5000),
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS messages_conversation_idx ON messages(conversation_id, created_at);

CREATE TABLE IF NOT EXISTS support_tickets (
  id uuid PRIMARY KEY,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  name varchar(160) NOT NULL,
  email varchar(320) NOT NULL,
  role varchar(60),
  topic varchar(120),
  subject varchar(180) NOT NULL,
  message text NOT NULL,
  status varchar(30) NOT NULL DEFAULT 'open',
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS audit_log (
  id uuid PRIMARY KEY,
  actor_user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  action varchar(160) NOT NULL,
  entity_type varchar(80),
  entity_id uuid,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS wishlist (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,course_id)
);

CREATE TABLE IF NOT EXISTS reviews (
  id uuid PRIMARY KEY,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
  review text,
  status varchar(20) NOT NULL DEFAULT 'published',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(student_id,course_id)
);

CREATE TABLE IF NOT EXISTS live_classes (
  id uuid PRIMARY KEY,
  instructor_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid REFERENCES courses(id) ON DELETE SET NULL,
  title varchar(220) NOT NULL,
  description text,
  starts_at timestamptz NOT NULL,
  duration_minutes integer NOT NULL DEFAULT 60 CHECK(duration_minutes BETWEEN 10 AND 480),
  capacity integer NOT NULL DEFAULT 50 CHECK(capacity BETWEEN 1 AND 10000),
  status varchar(20) NOT NULL DEFAULT 'upcoming',
  meeting_provider varchar(40),
  meeting_ref text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS live_class_attendance (
  live_class_id uuid NOT NULL REFERENCES live_classes(id) ON DELETE CASCADE,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reserved_at timestamptz NOT NULL DEFAULT now(),
  joined_at timestamptz,
  left_at timestamptz,
  PRIMARY KEY(live_class_id,student_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type varchar(80) NOT NULL,
  title varchar(180) NOT NULL,
  body text,
  link text,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notifications_user_idx ON notifications(user_id,created_at DESC);

CREATE TABLE IF NOT EXISTS instructor_payouts (
  id uuid PRIMARY KEY,
  instructor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  amount_cents integer NOT NULL CHECK(amount_cents >= 0),
  currency char(3) NOT NULL DEFAULT 'USD',
  status varchar(30) NOT NULL DEFAULT 'pending',
  reference varchar(120),
  period_start date,
  period_end date,
  created_at timestamptz NOT NULL DEFAULT now(),
  paid_at timestamptz
);

-- Phase 11 learning-content infrastructure
CREATE TABLE IF NOT EXISTS course_sections (
  id uuid PRIMARY KEY,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  title varchar(220) NOT NULL,
  position integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS course_sections_course_idx ON course_sections(course_id,position);

CREATE TABLE IF NOT EXISTS course_assets (
  id uuid PRIMARY KEY,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  uploaded_by uuid REFERENCES users(id) ON DELETE SET NULL,
  kind varchar(30) NOT NULL CHECK(kind IN ('video','document','audio','thumbnail','other')),
  original_name varchar(255) NOT NULL,
  stored_name varchar(255) NOT NULL UNIQUE,
  mime_type varchar(160) NOT NULL,
  size_bytes bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS course_assets_course_idx ON course_assets(course_id,created_at DESC);

CREATE TABLE IF NOT EXISTS lessons (
  id uuid PRIMARY KEY,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  section_id uuid REFERENCES course_sections(id) ON DELETE SET NULL,
  title varchar(220) NOT NULL,
  lesson_type varchar(30) NOT NULL DEFAULT 'video' CHECK(lesson_type IN ('video','article','quiz','assignment','resource')),
  position integer NOT NULL DEFAULT 0,
  asset_id uuid REFERENCES course_assets(id) ON DELETE SET NULL,
  body text,
  duration_seconds integer NOT NULL DEFAULT 0,
  is_preview boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS lessons_course_idx ON lessons(course_id,position);

CREATE TABLE IF NOT EXISTS lesson_progress (
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  lesson_id uuid NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
  completed boolean NOT NULL DEFAULT false,
  position_seconds integer NOT NULL DEFAULT 0,
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  PRIMARY KEY(student_id,lesson_id)
);

CREATE TABLE IF NOT EXISTS assessments (
  id uuid PRIMARY KEY,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  lesson_id uuid REFERENCES lessons(id) ON DELETE CASCADE,
  title varchar(220) NOT NULL,
  pass_mark smallint NOT NULL DEFAULT 50 CHECK(pass_mark BETWEEN 0 AND 100),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS assessment_questions (
  id uuid PRIMARY KEY,
  assessment_id uuid NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
  prompt text NOT NULL,
  options jsonb NOT NULL DEFAULT '[]'::jsonb,
  correct_index smallint,
  position integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS assessment_attempts (
  id uuid PRIMARY KEY,
  assessment_id uuid NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  answers jsonb NOT NULL DEFAULT '[]'::jsonb,
  score smallint NOT NULL CHECK(score BETWEEN 0 AND 100),
  passed boolean NOT NULL DEFAULT false,
  submitted_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS certificates (
  id uuid PRIMARY KEY,
  certificate_code varchar(80) UNIQUE NOT NULL,
  student_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  issued_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(student_id,course_id)
);


-- Phase 12 commercial operations
ALTER TABLE orders ADD COLUMN IF NOT EXISTS subtotal_cents integer NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_cents integer NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS platform_fee_cents integer NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code varchar(60);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reference varchar(160);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE TABLE IF NOT EXISTS coupons (
  id uuid PRIMARY KEY,
  code varchar(60) UNIQUE NOT NULL,
  description varchar(220),
  discount_type varchar(20) NOT NULL CHECK(discount_type IN ('percent','fixed')),
  discount_value integer NOT NULL CHECK(discount_value > 0),
  active boolean NOT NULL DEFAULT true,
  starts_at timestamptz,
  ends_at timestamptz,
  max_redemptions integer,
  created_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS coupon_redemptions (
  coupon_id uuid NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  redeemed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(coupon_id,order_id)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  provider varchar(40) NOT NULL,
  provider_reference varchar(180),
  amount_cents integer NOT NULL CHECK(amount_cents >= 0),
  currency char(3) NOT NULL DEFAULT 'USD',
  status varchar(30) NOT NULL DEFAULT 'pending',
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS payment_transactions_order_idx ON payment_transactions(order_id,created_at DESC);

CREATE TABLE IF NOT EXISTS instructor_earnings (
  id uuid PRIMARY KEY,
  instructor_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
  order_item_id uuid NOT NULL UNIQUE REFERENCES order_items(id) ON DELETE RESTRICT,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
  gross_cents integer NOT NULL,
  instructor_cents integer NOT NULL,
  platform_cents integer NOT NULL,
  status varchar(30) NOT NULL DEFAULT 'available' CHECK(status IN ('pending','available','paid','reversed','held')),
  available_at timestamptz NOT NULL DEFAULT now(),
  payout_id uuid REFERENCES instructor_payouts(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS instructor_earnings_instructor_idx ON instructor_earnings(instructor_id,status,created_at DESC);

CREATE TABLE IF NOT EXISTS refunds (
  id uuid PRIMARY KEY,
  order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
  requested_by uuid REFERENCES users(id) ON DELETE SET NULL,
  amount_cents integer NOT NULL CHECK(amount_cents >= 0),
  reason text,
  status varchar(30) NOT NULL DEFAULT 'requested' CHECK(status IN ('requested','approved','rejected','processed')),
  admin_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  resolved_at timestamptz
);
CREATE INDEX IF NOT EXISTS refunds_order_idx ON refunds(order_id,created_at DESC);

-- Phase 13 live learning infrastructure
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS session_type varchar(40) NOT NULL DEFAULT 'group_class';
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS timezone varchar(80) NOT NULL DEFAULT 'Africa/Harare';
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS join_url text;
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS host_url text;
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS recording_url text;
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS recording_status varchar(30) NOT NULL DEFAULT 'none';
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS allow_recording boolean NOT NULL DEFAULT false;
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS ended_at timestamptz;
ALTER TABLE live_classes ADD COLUMN IF NOT EXISTS cancelled_at timestamptz;
ALTER TABLE live_class_attendance ADD COLUMN IF NOT EXISTS last_joined_at timestamptz;
ALTER TABLE live_class_attendance ADD COLUMN IF NOT EXISTS join_count integer NOT NULL DEFAULT 0;
ALTER TABLE live_class_attendance ADD COLUMN IF NOT EXISTS attendance_minutes integer NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS live_class_messages (
  id uuid PRIMARY KEY,
  live_class_id uuid NOT NULL REFERENCES live_classes(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS live_class_messages_session_idx ON live_class_messages(live_class_id,created_at ASC);
CREATE INDEX IF NOT EXISTS live_classes_start_idx ON live_classes(starts_at,status);
CREATE INDEX IF NOT EXISTS live_class_attendance_student_idx ON live_class_attendance(student_id,reserved_at DESC);

-- Phase 14 communications and notification infrastructure
CREATE TABLE IF NOT EXISTS notification_preferences (
  user_id uuid PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  in_app_messages boolean NOT NULL DEFAULT true,
  email_messages boolean NOT NULL DEFAULT true,
  in_app_announcements boolean NOT NULL DEFAULT true,
  email_announcements boolean NOT NULL DEFAULT true,
  in_app_live_classes boolean NOT NULL DEFAULT true,
  email_live_classes boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS course_announcements (
  id uuid PRIMARY KEY,
  course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  title varchar(180) NOT NULL,
  body text NOT NULL,
  email_students boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS course_announcements_course_idx ON course_announcements(course_id,created_at DESC);

CREATE TABLE IF NOT EXISTS email_delivery_log (
  id uuid PRIMARY KEY,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  email varchar(320) NOT NULL,
  template varchar(100) NOT NULL,
  subject varchar(220) NOT NULL,
  status varchar(40) NOT NULL,
  provider_reference varchar(200),
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS email_delivery_log_user_idx ON email_delivery_log(user_id,created_at DESC);

-- Phase 15 production hardening
CREATE TABLE IF NOT EXISTS security_events (
  id uuid PRIMARY KEY,
  user_id uuid REFERENCES users(id) ON DELETE SET NULL,
  event_type varchar(100) NOT NULL,
  ip_address varchar(120),
  user_agent varchar(500),
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS security_events_type_idx ON security_events(event_type,created_at DESC);
CREATE INDEX IF NOT EXISTS security_events_user_idx ON security_events(user_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);


-- Phase 16 launch controls
CREATE TABLE IF NOT EXISTS platform_launch_state (
  id smallint PRIMARY KEY DEFAULT 1 CHECK(id=1),
  status varchar(20) NOT NULL DEFAULT 'prelaunch' CHECK(status IN ('prelaunch','live','maintenance')),
  registrations_open boolean NOT NULL DEFAULT true,
  instructor_applications_open boolean NOT NULL DEFAULT true,
  public_certificate_verification boolean NOT NULL DEFAULT true,
  launch_message varchar(300) NOT NULL DEFAULT 'EduQuinn is preparing for launch.',
  launched_at timestamptz,
  updated_by uuid REFERENCES users(id) ON DELETE SET NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO platform_launch_state(id) VALUES(1) ON CONFLICT(id) DO NOTHING;

-- Advanced lesson and quiz builder

ALTER TABLE assessments
  ADD COLUMN IF NOT EXISTS instructions text;

ALTER TABLE assessments
  ADD COLUMN IF NOT EXISTS max_attempts integer
  NOT NULL DEFAULT 3;

ALTER TABLE assessments
  ADD COLUMN IF NOT EXISTS time_limit_minutes integer
  NOT NULL DEFAULT 0;

ALTER TABLE assessments
  ADD COLUMN IF NOT EXISTS shuffle_questions boolean
  NOT NULL DEFAULT false;

ALTER TABLE assessments
  ADD COLUMN IF NOT EXISTS show_answers boolean
  NOT NULL DEFAULT true;


ALTER TABLE assessment_questions
  ADD COLUMN IF NOT EXISTS question_type varchar(30)
  NOT NULL DEFAULT 'single';

ALTER TABLE assessment_questions
  ADD COLUMN IF NOT EXISTS correct_answers jsonb
  NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE assessment_questions
  ADD COLUMN IF NOT EXISTS marks integer
  NOT NULL DEFAULT 1;

ALTER TABLE assessment_questions
  ADD COLUMN IF NOT EXISTS explanation text;


ALTER TABLE lessons
  ADD COLUMN IF NOT EXISTS metadata jsonb
  NOT NULL DEFAULT '{}'::jsonb;
