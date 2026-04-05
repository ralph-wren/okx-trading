create table backtest_equity_curve
(id             bigint auto_increment primary key,
 backtest_id    varchar(255)   not null,
 timestamp      datetime       not null,
 equity_value   decimal(20, 8) not null,
 index_position int            null) comment '回测资金曲线数据表' collate = utf8mb4_unicode_ci;

create index idx_backtest_id on backtest_equity_curve (backtest_id);

create index idx_timestamp on backtest_equity_curve (timestamp);

create table backtest_summary
(id                    bigint auto_increment primary key,
 average_profit        decimal(10, 4) null,
 backtest_id           varchar(255)   not null,
 create_time           datetime       null,
 end_time              datetime       null,
 final_amount          decimal(20, 8) null,
 initial_amount        decimal(20, 8) not null,
 interval_val          varchar(255)   not null,
 max_drawdown          decimal(10, 4) null,
 max_drawdown_period   decimal(10, 4) null,
 number_of_trades      int            null,
 profitable_trades     int            null,
 sharpe_ratio          decimal(10, 4) null,
 start_time            datetime       null,
 strategy_name         varchar(255)   not null,
 strategy_code         varchar(255)   not null,
 strategy_params       varchar(255)   null,
 symbol                varchar(255)   not null,
 total_profit          decimal(20, 8) null,
 total_return          decimal(10, 4) null,
 unprofitable_trades   int            null,
 win_rate              decimal(10, 4) null,
 total_fee             decimal(20, 8) null,
 batch_backtest_id     varchar(255)   null,
 annualized_return     decimal(10, 4) null,
 calmar_ratio          decimal(10, 4) null,
 maximum_loss          decimal(20, 8) null,
 maximum_loss_period   decimal(20, 8) null,
 sortino_ratio         decimal(10, 4) null,
 volatility            decimal(10, 4) null,
 omega                 decimal(10, 4) null comment 'Omega比率（收益与风险的比值）',
 alpha                 decimal(10, 4) null comment 'Alpha值（超额收益）',
 beta                  decimal(10, 4) null comment 'Beta值（系统性风险）',
 treynor_ratio         decimal(10, 4) null comment 'Treynor比率（风险调整收益指标）',
 ulcer_index           decimal(10, 4) null comment 'Ulcer指数（回撤深度和持续时间的综合指标）',
 skewness              decimal(10, 4) null comment '偏度（收益分布的偏斜程度）',
 profit_factor         decimal(10, 4) null comment '盈利因子（总盈利/总亏损）',
 burke_ratio           decimal(10, 4) null,
 comprehensive_score   decimal(3, 2)  null,
 cvar                  decimal(10, 4) null,
 downside_deviation    decimal(10, 4) null,
 downtrend_capture     decimal(10, 4) null,
 information_ratio     decimal(10, 4) null,
 kurtosis              decimal(10, 4) null,
 max_drawdown_duration decimal(10, 2) null,
 modified_sharpe_ratio decimal(10, 4) null,
 pain_index            decimal(10, 4) null,
 risk_adjusted_return  decimal(10, 4) null,
 sterling_ratio        decimal(10, 4) null,
 tracking_error        decimal(10, 4) null,
 uptrend_capture       decimal(10, 4) null,
 var95                 decimal(10, 4) null,
 var99                 decimal(10, 4) null,
 is_real               int default -1 null,
 constraint UK_pejcjjk0mdb200ay5mffbomkt unique (backtest_id));

create index backtest_summary__index on backtest_summary (total_return);

create index backtest_summary_annualized_return_index on backtest_summary (annualized_return);

create index backtest_summary_batch_backtest_id_annualized_return_index on backtest_summary (batch_backtest_id, annualized_return);

create index backtest_summary_batch_backtest_id_create_time_index on backtest_summary (batch_backtest_id, create_time);

create index backtest_summary_create_time_index on backtest_summary (create_time);

create index backtest_summary_id_create_time_index on backtest_summary (id, create_time);

create index backtest_summary_strategy_code_index on backtest_summary (strategy_code);

create index backtest_summary_strategy_name_index on backtest_summary (strategy_name);

create index backtest_summary_symbol_interval_val_index on backtest_summary (symbol, interval_val);

create index backtest_summary_total_return_index on backtest_summary (total_return);

create table backtest_trade
(id                           bigint auto_increment primary key,
 backtest_id                  varchar(255)   not null,
 closed                       bit            null,
 create_time                  datetime       null,
 entry_amount                 decimal(20, 8) null,
 entry_position_percentage    decimal(10, 4) null,
 entry_price                  decimal(20, 8) null,
 entry_time                   datetime       null,
 exit_amount                  decimal(20, 8) null,
 exit_price                   decimal(20, 8) null,
 exit_time                    datetime       null,
 fee                          decimal(20, 8) null,
 trade_index                  int            null,
 max_drawdown                 decimal(10, 4) null,
 max_loss                     decimal(10, 4) null,
 max_drawdown_period          decimal(10, 4) null,
 max_loss_period              decimal(10, 4) null,
 profit                       decimal(20, 8) null,
 profit_percentage            decimal(10, 4) null,
 periods                      decimal(10, 4) null,
 profit_percentage_per_period decimal(10, 4) null,
 remark                       varchar(500)   null,
 strategy_name                varchar(255)   not null,
 strategy_code                varchar(255)   not null,
 strategy_params              varchar(255)   null,
 symbol                       varchar(255)   not null,
 total_assets                 decimal(20, 8) null,
 trade_type                   varchar(255)   not null,
 volume                       decimal(20, 8) null);

create index backtest_trade__index on backtest_trade (entry_time);

create index backtest_trade_backtest_id_index on backtest_trade (backtest_id);

create index backtest_trade_exit_time_index on backtest_trade (exit_time);

create index backtest_trade_profit_percentage_index on backtest_trade (profit_percentage);

create index backtest_trade_strategy_code_index on backtest_trade (strategy_code);

create table candlestick_history
(id           bigint auto_increment primary key,
 close        decimal(30, 15) null,
 close_time   datetime        null,
 fetch_time   datetime        null,
 high         decimal(30, 15) null,
 interval_val varchar(10)     not null,
 low          decimal(30, 15) null,
 open         decimal(30, 15) null,
 open_time    datetime        not null,
 quote_volume decimal(30, 15) null,
 symbol       varchar(20)     not null,
 trades       bigint          null,
 volume       decimal(30, 15) null);

create table fund_data
(id               bigint auto_increment primary key,
 record_time      datetime(6)    not null,
 total_fund       decimal(20, 8) not null,
 total_investment decimal(20, 8) not null,
 total_profit     decimal(20, 8) not null);

create index fund_data_id_record_time_index on fund_data (id, record_time);

create table indicator_distribution
(id                     bigint auto_increment primary key,
 avg_value              decimal(20, 8)                           null,
 create_time            datetime(6)                              not null,
 indicator_display_name varchar(100)                             null,
 indicator_name         varchar(100)                             not null,
 indicator_type         enum ('NEGATIVE', 'NEUTRAL', 'POSITIVE') not null,
 is_current             bit                                      not null,
 max_value              decimal(20, 8)                           null,
 min_value              decimal(20, 8)                           null,
 p10                    decimal(20, 8)                           null,
 p20                    decimal(20, 8)                           null,
 p30                    decimal(20, 8)                           null,
 p40                    decimal(20, 8)                           null,
 p50                    decimal(20, 8)                           null,
 p60                    decimal(20, 8)                           null,
 p70                    decimal(20, 8)                           null,
 p80                    decimal(20, 8)                           null,
 p90                    decimal(20, 8)                           null,
 sample_count           int                                      not null,
 update_time            datetime(6)                              not null,
 version                bigint                                   not null);

create index indicator_distribution_indicator_name_index on indicator_distribution (indicator_name);

create index indicator_distribution_indicator_name_is_current_index on indicator_distribution (indicator_name, is_current);

create index indicator_distribution_is_current_index on indicator_distribution (is_current);

create index indicator_distribution_version_index on indicator_distribution (version);

create table real_time_orders
(id              bigint auto_increment primary key,
 strategy_id     bigint          null,
 client_order_id varchar(50)     null,
 pre_amount      decimal(20, 12) null,
 pre_quantity    decimal(20, 12) null,
 executed_amount decimal(20, 12) null,
 executed_qty    decimal(20, 12) null,
 price           decimal(20, 12) null,
 fee             decimal(20, 12) null,
 fee_currency    varchar(10)     null,
 order_id        varchar(50)     null,
 order_type      varchar(50)     null,
 side            varchar(10)     not null,
 profit          decimal(20, 12) null,
 profit_rate     decimal(20, 12) null,
 signal_price    decimal(20, 12) null,
 signal_type     varchar(20)     null,
 strategy_code   varchar(50)     not null,
 symbol          varchar(20)     not null,
 status          varchar(20)     null,
 create_time     datetime(6)     not null,
 singal_time     datetime(6)     not null,
 update_time     datetime(6)     null);

create index real_time_orders_client_order_id_index on real_time_orders (client_order_id);

create index real_time_orders_create_time_index on real_time_orders (create_time);

create index real_time_orders_strategy_id_create_time_index on real_time_orders (strategy_id, create_time);

create index real_time_strategy__index on real_time_orders (strategy_id);

create table real_time_strategy
(id                  bigint auto_increment primary key,
 strategy_code       varchar(50)  not null,
 strategy_name       varchar(50)  null,
 symbol              varchar(20)  not null,
 interval_val        varchar(10)  not null,
 trade_amount        double       null,
 last_trade_amount   double       null,
 last_trade_price    double       null,
 last_trade_quantity double       null,
 last_trade_type     varchar(10)  null,
 last_trade_fee      double       null,
 last_trade_time     datetime(6)  null,
 last_trade_profit   double       null,
 total_profit        double       null,
 total_profit_rate   double       null,
 total_fees          double       null,
 total_trades        int          null,
 successful_trades   int          null,
 is_active           tinyint(1)   null,
 status              varchar(20)  null,
 error_message       text collate utf8mb4_unicode_ci null,
 start_time          datetime(6)  not null,
 end_time            datetime(6)  null,
 create_time         datetime(6)  not null,
 update_time         datetime(6)  not null,
 constraint UK_strategy_code unique (strategy_code));

create index idx_real_time_strategy_symbol_trade_type on real_time_strategy (symbol, last_trade_type);

create index idx_real_time_strategy_trade_type on real_time_strategy (last_trade_type);

create index idx_real_time_strategy_status on real_time_strategy (status);

create index idx_real_time_strategy_is_active on real_time_strategy (is_active);

create index idx_real_time_strategy_create_time on real_time_strategy (create_time);

create table strategy_conversation
(id                bigint auto_increment primary key,
 ai_response       text collate utf8mb4_unicode_ci null,
 conversation_type varchar(255)                    not null,
 create_time       datetime                        not null,
 strategy_id       bigint                          not null,
 user_input        text collate utf8mb4_unicode_ci null,
 compile_error     text collate utf8mb4_unicode_ci null);

create index idx_strategy_conversation_strategy_id on strategy_conversation (strategy_id);

create index idx_strategy_conversation_type on strategy_conversation (conversation_type);

create table telegram_channels
(id           bigint auto_increment primary key,
 channel_name varchar(255) not null,
 title        varchar(255) null,
 subscribers  bigint       null,
 avatar_url   varchar(500) null,
 description  text collate utf8mb4_unicode_ci null,
 is_active    tinyint(1) default 1 not null,
 constraint UK_telegram_channel_name unique (channel_name));

create index idx_telegram_channels_is_active on telegram_channels (is_active);

create table telegram_messages
(id           bigint auto_increment primary key,
 chat_id      bigint       null,
 chat_title   varchar(255) null,
 message_id   int          null,
 text         text collate utf8mb4_unicode_ci null,
 sender_name  varchar(255) null,
 sender_username varchar(255) null,
 received_at  datetime(6)  null,
 message_date datetime(6)  null);

create index idx_telegram_messages_chat_id on telegram_messages (chat_id);

create index idx_telegram_messages_received_at on telegram_messages (received_at);

create index idx_telegram_messages_message_date on telegram_messages (message_date);

create table strategy_info
(id             bigint auto_increment primary key,
 category       varchar(50) collate utf8mb4_unicode_ci null,
 create_time    datetime                        not null,
 default_params varchar(255) collate utf8mb4_unicode_ci null,
 description    text collate utf8mb4_unicode_ci null,
 comments       text collate utf8mb4_unicode_ci null,
 params_desc    text collate utf8mb4_unicode_ci null,
 strategy_code  varchar(500)                    not null,
 strategy_name  varchar(100) collate utf8mb4_unicode_ci not null,
 update_time    datetime                        not null,
 source_code    text                            null,
 load_error     text collate utf8mb4_unicode_ci null,
 constraint UK_2n6paqskn3ck3x2f18i56esxp unique (strategy_code));

create index idx_strategy_info_category on strategy_info (category);

create table trades
(id           bigint auto_increment primary key,
 created_at   datetime(6)    null,
 fee          decimal(20, 8) null,
 is_simulated bit            not null,
 order_id     varchar(255)   null,
 pl           decimal(20, 8) null,
 price        decimal(20, 8) not null,
 side         varchar(255)   not null,
 size         decimal(20, 8) not null,
 status       varchar(255)   not null,
 symbol       varchar(255)   not null,
 trade_time   datetime(6)    not null,
 type         varchar(255)   not null,
 updated_at   datetime(6)    null,
 backtest_id  bigint         null,
 strategy_id  bigint         null,
 user_id      bigint         not null);

create index FKm0qyc8sdm47tm473sffdb3pyi on trades (strategy_id);

create index FKof2p7ht9xpwtu4myqv787bbr8 on trades (user_id);

create index FKssslx7sldq4smb7m0th3fym9h on trades (backtest_id);

create table users
(id         bigint auto_increment primary key,
 created_at datetime(6)  null,
 email      varchar(255) null,
 full_name  varchar(255) null,
 password   varchar(255) not null,
 updated_at datetime(6)  null,
 username   varchar(255) not null,
 constraint UK_6dotkott2kjsp8vw4d0m25fb7 unique (email),
 constraint UK_r43af9ap4edm43mmtq01oddj6 unique (username));

create table user_api_keys
(id           bigint auto_increment primary key,
 api_key      varchar(255) not null,
 api_secret   varchar(255) not null,
 created_at   datetime(6)  null,
 description  varchar(255) null,
 is_simulated bit          not null,
 passphrase   varchar(255) null,
 updated_at   datetime(6)  null,
 user_id      bigint       not null,
 constraint FKs49bj0ss79ya93wgj7k5synxc foreign key (user_id)
     references users (id));
