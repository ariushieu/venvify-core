
    create table bookings (
        attendee_id bigint not null,
        booked_at datetime(6) not null,
        created_at datetime(6) not null,
        event_id bigint not null,
        id bigint not null auto_increment,
        price_paid bigint not null,
        purchase_txn_id bigint,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        status enum ('ATTENDED','CANCELLED','CONFIRMED','NO_SHOW','REFUNDED','RESERVED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table chat_messages (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        room_id bigint not null,
        sender_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        content TEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table escrow_holds (
        booking_id bigint not null,
        commission_amount bigint not null,
        created_at datetime(6) not null,
        event_id bigint not null,
        gross_amount bigint not null,
        held_at datetime(6) not null,
        host_net_amount bigint not null,
        id bigint not null auto_increment,
        released_at datetime(6),
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        status enum ('HELD','PAID_OUT','REFUNDED','RELEASED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table events (
        claimed_slots integer not null,
        is_deleted bit not null,
        max_slots integer not null,
        created_at datetime(6) not null,
        end_time datetime(6) not null,
        host_id bigint not null,
        id bigint not null auto_increment,
        price_amount bigint not null,
        start_time datetime(6) not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        category varchar(50),
        title varchar(200) not null,
        slug varchar(220) not null,
        cover_image_url varchar(500),
        description TEXT,
        status enum ('CANCELLED','DRAFT','ENDED','LIVE','POSTPONED','PUBLISHED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table follows (
        created_at datetime(6) not null,
        follower_id bigint not null,
        host_id bigint not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        primary key (id)
    ) engine=InnoDB;

    create table ledger_entries (
        amount bigint not null,
        balance_after bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        transaction_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        wallet_id bigint not null,
        public_id varchar(36) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table notifications (
        is_read bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        related_entity_type varchar(30),
        public_id varchar(36) not null,
        related_entity_public_id varchar(36),
        title varchar(200) not null,
        content TEXT,
        type enum ('BOOKING_CONFIRMED','EVENT_CANCELLED','EVENT_POSTPONED','EVENT_REMINDER','EVENT_UPDATED','NEW_EVENT_FROM_FOLLOWED_HOST','PAYMENT_RECEIPT') not null,
        primary key (id)
    ) engine=InnoDB;

    create table poll_options (
        display_order integer not null,
        vote_count integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        poll_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        option_text varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table poll_votes (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        poll_id bigint not null,
        poll_option_id bigint not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        public_id varchar(36) not null,
        primary key (id)
    ) engine=InnoDB;

    create table polls (
        closed_at datetime(6),
        created_at datetime(6) not null,
        created_by bigint not null,
        id bigint not null auto_increment,
        room_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        question TEXT not null,
        status enum ('CLOSED','OPEN') not null,
        primary key (id)
    ) engine=InnoDB;

    create table question_upvotes (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        question_id bigint not null,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        public_id varchar(36) not null,
        primary key (id)
    ) engine=InnoDB;

    create table questions (
        upvote_count integer not null,
        answered_at datetime(6),
        asker_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        room_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        content TEXT not null,
        status enum ('ANSWERED','DISMISSED','PENDING') not null,
        primary key (id)
    ) engine=InnoDB;

    create table recordings (
        duration_seconds integer,
        created_at datetime(6) not null,
        event_id bigint not null,
        file_size_bytes bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        storage_url varchar(500),
        status enum ('FAILED','PROCESSING','READY') not null,
        primary key (id)
    ) engine=InnoDB;

    create table reviews (
        rating smallint not null,
        created_at datetime(6) not null,
        event_id bigint not null,
        host_id bigint not null,
        id bigint not null auto_increment,
        reviewer_id bigint not null,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        comment TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table rooms (
        created_at datetime(6) not null,
        ended_at datetime(6),
        event_id bigint not null,
        id bigint not null auto_increment,
        recording_id bigint,
        started_at datetime(6),
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        status enum ('ENDED','LIVE','WAITING') not null,
        primary key (id)
    ) engine=InnoDB;

    create table summaries (
        created_at datetime(6) not null,
        event_id bigint not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        version bigint not null,
        public_id varchar(36) not null,
        model_used varchar(50),
        transcript_url varchar(500),
        summary_content TEXT,
        top_questions TEXT,
        status enum ('FAILED','PENDING','READY') not null,
        primary key (id)
    ) engine=InnoDB;

    create table transactions (
        amount bigint not null,
        created_at datetime(6) not null,
        event_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        public_id varchar(36) not null,
        provider_txn_id varchar(100),
        transaction_ref varchar(100) not null,
        payment_provider enum ('INTERNAL','MOMO','VNPAY'),
        status enum ('CANCELLED','FAILED','PENDING','SUCCESS') not null,
        type enum ('COMMISSION','PAYOUT','REFUND','TICKET_PURCHASE','TOPUP') not null,
        primary key (id)
    ) engine=InnoDB;

    create table user_roles (
        user_id bigint not null,
        role enum ('ADMIN','ATTENDEE','HOST')
    ) engine=InnoDB;

    create table users (
        email_verified bit not null,
        is_deleted bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        version bigint not null,
        oauth_provider varchar(20),
        public_id varchar(36) not null,
        host_handle varchar(60),
        oauth_provider_id varchar(100),
        full_name varchar(150) not null,
        avatar_url varchar(500),
        bio TEXT,
        email varchar(255) not null,
        password_hash varchar(255),
        status enum ('ACTIVE','SUSPENDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table wallets (
        currency varchar(3) not null,
        balance_cached bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        version bigint not null,
        public_id varchar(36) not null,
        primary key (id)
    ) engine=InnoDB;

    create index idx_bookings_attendee 
       on bookings (attendee_id);

    create index idx_bookings_event 
       on bookings (event_id);

    alter table bookings 
       add constraint uq_booking_event_attendee unique (event_id, attendee_id);

    alter table bookings 
       add constraint UKpnhmc98qqel2wfy0qp852ukdy unique (public_id);

    create index idx_chat_room_created 
       on chat_messages (room_id, created_at);

    alter table chat_messages 
       add constraint UKaedp5lfifumia1j4p7jsgtjpp unique (public_id);

    create index idx_escrow_event 
       on escrow_holds (event_id);

    create index idx_escrow_booking 
       on escrow_holds (booking_id);

    create index idx_escrow_status 
       on escrow_holds (status);

    alter table escrow_holds 
       add constraint UKbd9ax3ugt97en2es8r9e1c7vn unique (public_id);

    create index idx_events_host 
       on events (host_id);

    create index idx_events_status 
       on events (status);

    create index idx_events_start 
       on events (start_time);

    create index idx_events_category 
       on events (category);

    alter table events 
       add constraint UKi4birsrlskr9r453y42sf94o8 unique (public_id);

    alter table events 
       add constraint UK153gnccmeyks5dlt154kkgch5 unique (slug);

    create index idx_follow_host 
       on follows (host_id);

    alter table follows 
       add constraint uq_follow_follower_host unique (follower_id, host_id);

    alter table follows 
       add constraint UKidowkwyhoc6lsdhwsgf2qltvj unique (public_id);

    create index idx_ledger_wallet 
       on ledger_entries (wallet_id, created_at);

    create index idx_ledger_txn 
       on ledger_entries (transaction_id);

    alter table ledger_entries 
       add constraint UKidinhkhviokspdt5l63r242nh unique (public_id);

    create index idx_notif_user_read 
       on notifications (user_id, is_read);

    create index idx_notif_user_created 
       on notifications (user_id, created_at);

    alter table notifications 
       add constraint UKqwcfwkb22prkvpgl4uyya4c8h unique (public_id);

    create index idx_polloption_poll 
       on poll_options (poll_id);

    alter table poll_options 
       add constraint UKeib116sgi7b3w37eejp2nm8dd unique (public_id);

    create index idx_pollvote_option 
       on poll_votes (poll_option_id);

    alter table poll_votes 
       add constraint uq_pollvote_poll_user unique (poll_id, user_id);

    alter table poll_votes 
       add constraint UKeeblyupxq8qf5q84an8qabafj unique (public_id);

    create index idx_polls_room 
       on polls (room_id);

    alter table polls 
       add constraint UK1axyut6pv93j06vx5d9i4ovsw unique (public_id);

    alter table question_upvotes 
       add constraint uq_qupvote_q_user unique (question_id, user_id);

    alter table question_upvotes 
       add constraint UKe547he5eav2f5q6vnvc0ri9t7 unique (public_id);

    create index idx_questions_room_status 
       on questions (room_id, status);

    create index idx_questions_room_upvotes 
       on questions (room_id, upvote_count);

    alter table questions 
       add constraint UK6cg9cf79leuopy266yko1gq0k unique (public_id);

    alter table recordings 
       add constraint uq_recording_event unique (event_id);

    alter table recordings 
       add constraint UKnny74upru16dmgjls249trt57 unique (public_id);

    create index idx_review_host 
       on reviews (host_id);

    create index idx_review_event 
       on reviews (event_id);

    alter table reviews 
       add constraint uq_review_event_reviewer unique (event_id, reviewer_id);

    alter table reviews 
       add constraint UK81rwvpfgcq2rmfkjm4o6lwps unique (public_id);

    alter table rooms 
       add constraint uq_room_event unique (event_id);

    alter table rooms 
       add constraint UK4f4jtyxbq1fc997a9huma22dx unique (public_id);

    alter table summaries 
       add constraint uq_summary_event unique (event_id);

    alter table summaries 
       add constraint UKwr7u6kpqwevkhcgcxcb6q8rp unique (public_id);

    create index idx_txn_type 
       on transactions (type);

    create index idx_txn_status 
       on transactions (status);

    create index idx_txn_user 
       on transactions (user_id);

    alter table transactions 
       add constraint uq_txn_ref unique (transaction_ref);

    alter table transactions 
       add constraint UK6ks0c2b0w4pnaq4v2heh7ychx unique (public_id);

    alter table user_roles 
       add constraint UKkpjrhqbpn9eqscssk9ttp7glu unique (user_id, role);

    create index idx_users_oauth 
       on users (oauth_provider, oauth_provider_id);

    alter table users 
       add constraint UKs24bux761rbgowsl7a4b386ba unique (public_id);

    alter table users 
       add constraint UK43iwrnmmyk68oxhmumo2sovd7 unique (host_handle);

    alter table users 
       add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);

    alter table wallets 
       add constraint uq_wallet_user unique (user_id);

    alter table wallets 
       add constraint UKolropkbk0v650qj5hl0qfvtu unique (public_id);

    alter table bookings 
       add constraint FKelcgrlelk5arghu5h32capih4 
       foreign key (attendee_id) 
       references users (id);

    alter table bookings 
       add constraint FK2ww82bk3npaiyu9oeehwtt2q3 
       foreign key (event_id) 
       references events (id);

    alter table bookings 
       add constraint FKq3hivcgbt58yeroo6rphasv1e 
       foreign key (purchase_txn_id) 
       references transactions (id);

    alter table chat_messages 
       add constraint FKpy62y5w8v8wfoygs3nk35i4y1 
       foreign key (room_id) 
       references rooms (id);

    alter table chat_messages 
       add constraint FKgiqeap8ays4lf684x7m0r2729 
       foreign key (sender_id) 
       references users (id);

    alter table escrow_holds 
       add constraint FK6eokc3j7we0futq5ohxhnpusx 
       foreign key (booking_id) 
       references bookings (id);

    alter table escrow_holds 
       add constraint FKbk36413s7kyjhrmnf9h3xgfss 
       foreign key (event_id) 
       references events (id);

    alter table events 
       add constraint FKnsvyk08eqkdlxp4d2httg2u8v 
       foreign key (host_id) 
       references users (id);

    alter table follows 
       add constraint FKqnkw0cwwh6572nyhvdjqlr163 
       foreign key (follower_id) 
       references users (id);

    alter table follows 
       add constraint FK8wg7gwp5wsifmy7pv08g1gs9k 
       foreign key (host_id) 
       references users (id);

    alter table ledger_entries 
       add constraint FKgwcsld4m3g325l66qro45i14x 
       foreign key (transaction_id) 
       references transactions (id);

    alter table ledger_entries 
       add constraint FKcfwn04lfp5eyco3vbjc3729ib 
       foreign key (wallet_id) 
       references wallets (id);

    alter table notifications 
       add constraint FK9y21adhxn0ayjhfocscqox7bh 
       foreign key (user_id) 
       references users (id);

    alter table poll_options 
       add constraint FK1baxdjoxricfu0grc0j6821f7 
       foreign key (poll_id) 
       references polls (id);

    alter table poll_votes 
       add constraint FKmaogo469u92y072mev488em6p 
       foreign key (poll_id) 
       references polls (id);

    alter table poll_votes 
       add constraint FKe95ke8vf97rs9i45igbq4o9x4 
       foreign key (poll_option_id) 
       references poll_options (id);

    alter table poll_votes 
       add constraint FK3q0e7cabgif9f1t7voom07bg5 
       foreign key (user_id) 
       references users (id);

    alter table polls 
       add constraint FKs2iay0nvudl3tl0a33ji0pxyn 
       foreign key (created_by) 
       references users (id);

    alter table polls 
       add constraint FKpd8pcuos42v9kn9kosjw0h5pl 
       foreign key (room_id) 
       references rooms (id);

    alter table question_upvotes 
       add constraint FKmpkc6k5gmcrco0cubgwe971ki 
       foreign key (question_id) 
       references questions (id);

    alter table question_upvotes 
       add constraint FKnwu2niyr7dtny6x3a5tocvmbj 
       foreign key (user_id) 
       references users (id);

    alter table questions 
       add constraint FKsat5fmui2uw8nnhan1dx817q1 
       foreign key (asker_id) 
       references users (id);

    alter table questions 
       add constraint FKjs48hpkafhbiusqvo6ed9gxoo 
       foreign key (room_id) 
       references rooms (id);

    alter table recordings 
       add constraint FKghileuf1xgemsqwyr4f2rkaa0 
       foreign key (event_id) 
       references events (id);

    alter table reviews 
       add constraint FKem6jjo18jyueiqhferf3dwfbx 
       foreign key (event_id) 
       references events (id);

    alter table reviews 
       add constraint FKoiqkuwwpbiv2qecmre0c4l9ef 
       foreign key (host_id) 
       references users (id);

    alter table reviews 
       add constraint FKd1isgfajhtdl8mgg29up6mofi 
       foreign key (reviewer_id) 
       references users (id);

    alter table rooms 
       add constraint FKdvkn7qdgby3lbmeim7vh93ywc 
       foreign key (event_id) 
       references events (id);

    alter table rooms 
       add constraint FK8a3efx3fq83l8oe70i9d9qnt 
       foreign key (recording_id) 
       references recordings (id);

    alter table summaries 
       add constraint FKmkg3d7o49be39lr3x6r6oixbj 
       foreign key (event_id) 
       references events (id);

    alter table transactions 
       add constraint FKe01y6qhe8lfkj74f0nac6mbmk 
       foreign key (event_id) 
       references events (id);

    alter table transactions 
       add constraint FKqwv7rmvc8va8rep7piikrojds 
       foreign key (user_id) 
       references users (id);

    alter table user_roles 
       add constraint FKhfh9dx7w3ubf1co1vdev94g3f 
       foreign key (user_id) 
       references users (id);

    alter table wallets 
       add constraint FKc1foyisidw7wqqrkamafuwn4e 
       foreign key (user_id) 
       references users (id);
