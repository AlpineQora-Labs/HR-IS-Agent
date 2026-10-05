-- Schools belong to a country, so a registration form can offer a student only the schools of
-- the country they chose (Country -> School). Schools already on record are in the US.
alter table school add column country varchar(2);
update school set country = 'US' where country is null;

-- A few schools outside the US, so the India and UK campus templates have something to offer.
insert into school (name, location, tier, country) values
 ('IIT Bombay',                        'Mumbai, India',      'CORE',   'IN'),
 ('IIT Delhi',                         'New Delhi, India',   'CORE',   'IN'),
 ('BITS Pilani',                       'Pilani, India',      'TARGET', 'IN'),
 ('London School of Economics',        'London, UK',         'CORE',   'GB'),
 ('Imperial College London',           'London, UK',         'CORE',   'GB'),
 ('University of Manchester',          'Manchester, UK',     'TARGET', 'GB')
on conflict (name) do nothing;
