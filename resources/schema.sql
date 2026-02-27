create table if not exists videos (
	id integer primary key autoincrement,
	storage_path text not null unique,
	filename text not null,
	duration_sec real,
	discovered_at text not null default current_timestamp
);

create table if not exists jobs (
	id integer primary key autoincrement,
	type text not null,
	video_id integer,
	payload_json text,
	status text not null,
	attempts integer not null default 0,
	error text,
	run_at text not null default current_timestamp,
	updated_at text not null default current_timestamp,
	foreign key(video_id) references videos(id)
);

create index if not exists idx_videos_storage_path on videos(storage_path);

create index if not exists idx_jobs_status_run_at on jobs(status, run_at);

create table if not exists thumbnails (
	id integer primary key autoincrement,
	video_id integer not null unique,
	image_blob blob not null,
	width integer,
	height integer,
	mime_type text not null default 'image/jpeg',
	generated_at text not null default current_timestamp,
	foreign key(video_id) references videos(id) on delete cascade
);

create index if not exists idx_thumbnails_video_id on thumbnails(video_id);
