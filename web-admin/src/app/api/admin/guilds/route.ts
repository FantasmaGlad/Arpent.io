import { NextResponse } from 'next/server';
import { supabaseAdmin } from '@/lib/supabaseAdmin';

// Helper function to check if the caller is the authorized admin
async function verifyAdmin(request: Request) {
  const authHeader = request.headers.get('Authorization');
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return false;
  }
  const token = authHeader.split(' ')[1];
  const { data: { user }, error } = await supabaseAdmin.auth.getUser(token);
  
  if (error || !user) {
    return false;
  }
  
  return user.email === 'clement.barillot3901@gmail.com';
}

export async function PUT(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  try {
    const { guildId, nom, couleurHex, avatarUrl, chefId } = await request.json();

    if (!guildId) {
      return NextResponse.json({ error: 'Guild ID is required' }, { status: 400 });
    }

    const updates: any = {};
    if (nom !== undefined) updates.nom = nom;
    if (couleurHex !== undefined) updates.couleur_hex = couleurHex;
    if (avatarUrl !== undefined) {
      updates.avatar_url = avatarUrl;
      if (avatarUrl === null) {
        // Fetch old avatar to delete from storage
        const { data: guild } = await supabaseAdmin
          .from('guildes')
          .select('avatar_url')
          .eq('id', guildId)
          .single();
        if (guild?.avatar_url) {
          const parts = guild.avatar_url.split('/');
          const fileName = parts[parts.length - 1];
          if (fileName) {
            // Remove cache busting query param if present
            const cleanFileName = fileName.split('?')[0];
            await supabaseAdmin.storage.from('Images').remove([cleanFileName]);
          }
        }
      }
    }
    if (chefId !== undefined) updates.chef_id = chefId === '' ? null : chefId;

    const { data, error } = await supabaseAdmin
      .from('guildes')
      .update(updates)
      .eq('id', guildId)
      .select()
      .single();

    if (error) {
      return NextResponse.json({ error: error.message }, { status: 400 });
    }

    return NextResponse.json({ success: true, guild: data });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function DELETE(request: Request) {
  const isAdmin = await verifyAdmin(request);
  if (!isAdmin) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }

  const { searchParams } = new URL(request.url);
  const guildId = searchParams.get('guildId');

  if (!guildId) {
    return NextResponse.json({ error: 'Guild ID is required' }, { status: 400 });
  }

  try {
    // 1. Get guild info to retrieve avatar URL
    const { data: guild } = await supabaseAdmin
      .from('guildes')
      .select('avatar_url')
      .eq('id', guildId)
      .single();

    // 2. Delete guild from database (foreign keys ON DELETE SET NULL handle users and territories)
    const { error: deleteError } = await supabaseAdmin
      .from('guildes')
      .delete()
      .eq('id', guildId);

    if (deleteError) {
      return NextResponse.json({ error: deleteError.message }, { status: 400 });
    }

    // 3. Clean up avatar in storage if it exists
    if (guild?.avatar_url) {
      const parts = guild.avatar_url.split('/');
      const fileName = parts[parts.length - 1];
      if (fileName) {
        const cleanFileName = fileName.split('?')[0];
        await supabaseAdmin.storage.from('Images').remove([cleanFileName]);
      }
    }

    return NextResponse.json({ success: true });
  } catch (err: any) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
