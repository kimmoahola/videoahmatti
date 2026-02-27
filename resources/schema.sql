create table if not exists videos (
	id integer primary key autoincrement,
	storage_path text not null unique,
	filename text not null,
	duration_sec real,
	discovered_at text not null default current_timestamp
);

create index if not exists idx_videos_storage_path on videos(storage_path);

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
